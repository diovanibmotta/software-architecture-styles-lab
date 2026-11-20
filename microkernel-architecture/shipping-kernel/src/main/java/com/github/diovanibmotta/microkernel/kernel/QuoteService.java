package com.github.diovanibmotta.microkernel.kernel;

import com.github.diovanibmotta.microkernel.api.Quote;
import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import com.github.diovanibmotta.microkernel.api.ShippingPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * Core business flow: fan the request out to every active plug-in, in parallel, each with its own time budget.
 * A plug-in that throws or hangs is reported in {@code failures} and never breaks the response.
 */
@Service
public class QuoteService {

    public record PluginQuote(String pluginId, Quote quote) {}

    public record PluginFailure(String pluginId, String reason) {}

    public record Result(List<PluginQuote> quotes, List<PluginFailure> failures) {}

    private final PluginManager manager;
    private final KernelProperties props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public QuoteService(PluginManager manager, KernelProperties props) {
        this.manager = manager;
        this.props = props;
    }

    public Result quote(QuoteRequest request) {
        List<ShippingPlugin> plugins = manager.plugins();
        List<Future<List<Quote>>> futures = plugins.stream()
                .map(p -> executor.submit(() -> p.quote(request))).toList();

        List<PluginQuote> quotes = new ArrayList<>();
        List<PluginFailure> failures = new ArrayList<>();
        for (int i = 0; i < plugins.size(); i++) {
            String id = plugins.get(i).id();
            try {
                // Each future already runs concurrently; the wait is a per-plug-in ceiling, not a sum.
                futures.get(i).get(props.pluginTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .forEach(q -> quotes.add(new PluginQuote(id, q)));
            } catch (TimeoutException e) {
                futures.get(i).cancel(true);
                failures.add(new PluginFailure(id, "timeout after " + props.pluginTimeout()));
            } catch (ExecutionException e) {
                failures.add(new PluginFailure(id, String.valueOf(e.getCause())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(new PluginFailure(id, "interrupted"));
                break;
            }
        }
        quotes.sort(Comparator.comparing((PluginQuote q) -> q.quote().price()));
        return new Result(quotes, failures);
    }
}
