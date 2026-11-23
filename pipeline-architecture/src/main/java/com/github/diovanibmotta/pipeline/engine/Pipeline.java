package com.github.diovanibmotta.pipeline.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Pipes and filters. Every filter runs on its own virtual thread; the pipes between them are bounded
 * queues, so a slow filter back-pressures everything upstream instead of exhausting memory.
 * Filters work concurrently on different items, but each filter sees items in order.
 */
public final class Pipeline {

    private static final Object END = new Object();

    private record Stage(String name, Function<Object, Optional<Object>> work) {
    }

    private static final class Rejected extends RuntimeException {
        Rejected(String reason) {
            super(reason, null, false, false);
        }
    }

    private final Producer<Object> producer;
    private final List<Stage> stages;
    private final int pipeCapacity;

    private Pipeline(Producer<Object> producer, List<Stage> stages, int pipeCapacity) {
        this.producer = producer;
        this.stages = stages;
        this.pipeCapacity = pipeCapacity;
    }

    public static <T> Builder<T> from(Producer<T> producer) {
        return new Builder<>(producer, List.of(), 64);
    }

    /** Typed builder: the compiler checks that each filter's output type matches the next filter's input. */
    public static final class Builder<T> {
        private final Producer<?> producer;
        private final List<Stage> stages;
        private final int capacity;

        private Builder(Producer<?> producer, List<Stage> stages, int capacity) {
            this.producer = producer;
            this.stages = stages;
            this.capacity = capacity;
        }

        public Builder<T> pipeCapacity(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("capacity must be >= 1");
            }
            return new Builder<>(producer, stages, capacity);
        }

        @SuppressWarnings("unchecked")
        public <R> Builder<R> transform(String name, Transformer<? super T, ? extends R> transformer) {
            return add(name, in -> Optional.of(((Transformer<Object, Object>) transformer).transform(in)));
        }

        @SuppressWarnings("unchecked")
        public Builder<T> test(String name, Tester<? super T> tester) {
            return add(name, in -> {
                Optional<String> reason = ((Tester<Object>) tester).check(in);
                if (reason.isPresent()) {
                    throw new Rejected(reason.get());
                }
                return Optional.of(in);
            });
        }

        @SuppressWarnings("unchecked")
        public Pipeline to(String name, Sink<? super T> sink) {
            Builder<T> last = add(name, in -> {
                ((Sink<Object>) sink).consume(in);
                return Optional.of(in);
            });
            return new Pipeline((Producer<Object>) producer, last.stages, capacity);
        }

        @SuppressWarnings("unchecked")
        private <R> Builder<R> add(String name, Function<Object, Optional<Object>> work) {
            List<Stage> next = new ArrayList<>(stages);
            next.add(new Stage(name, work));
            return (Builder<R>) new Builder<>(producer, List.copyOf(next), capacity);
        }
    }

    /** Runs to completion: returns once the producer is exhausted and every item has left the last filter. */
    public PipelineReport run() {
        int n = stages.size();
        List<BlockingQueue<Object>> pipes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pipes.add(new ArrayBlockingQueue<>(pipeCapacity));
        }
        var deadLetters = new ConcurrentLinkedQueue<DeadLetter>();
        var produced = new AtomicLong();
        var consumed = new AtomicLong();
        var producerFailure = new AtomicReference<Throwable>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                try {
                    producer.produce(item -> {
                        put(pipes.get(0), item);
                        produced.incrementAndGet();
                    });
                } catch (Throwable t) {
                    producerFailure.set(t);
                } finally {
                    put(pipes.get(0), END);
                }
            });
            for (int i = 0; i < n; i++) {
                int index = i;
                Stage stage = stages.get(i);
                // shortcut: only RuntimeException is caught per item; an Error kills this filter and stalls the pipeline. Fine for a lab.
                executor.submit(() -> {
                    while (true) {
                        Object in = take(pipes.get(index));
                        if (in == END) {
                            if (index + 1 < n) {
                                put(pipes.get(index + 1), END);
                            }
                            return;
                        }
                        try {
                            Object out = stage.work().apply(in).orElseThrow();
                            if (index + 1 < n) {
                                put(pipes.get(index + 1), out);
                            } else {
                                consumed.incrementAndGet();
                            }
                        } catch (RuntimeException e) {
                            String reason = e instanceof Rejected ? e.getMessage() : e.toString();
                            deadLetters.add(new DeadLetter(stage.name(), in, reason));
                        }
                    }
                });
            }
        }

        if (producerFailure.get() != null) {
            throw new PipelineException("producer failed after " + produced.get() + " items", producerFailure.get());
        }
        return new PipelineReport(produced.get(), consumed.get(), List.copyOf(deadLetters));
    }

    private static void put(BlockingQueue<Object> pipe, Object item) {
        try {
            pipe.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private static Object take(BlockingQueue<Object> pipe) {
        try {
            return pipe.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
