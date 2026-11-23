package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Producer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

/** Producer: emits the raw data lines of a CSV (header and blank lines skipped). The caller owns the reader. */
public class CsvLinesProducer implements Producer<String> {

    private final BufferedReader reader;

    public CsvLinesProducer(BufferedReader reader) {
        this.reader = reader;
    }

    @Override
    public void produce(Consumer<String> emit) throws IOException {
        reader.readLine(); // header
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                emit.accept(line);
            }
        }
    }
}
