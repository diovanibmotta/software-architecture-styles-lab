package com.github.diovanibmotta.microkernel.kernel;

import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KernelController {

    public record QuoteBody(
            @Pattern(regexp = "\\d{5}") String originZip,
            @Pattern(regexp = "\\d{5}") String destinationZip,
            @DecimalMin(value = "0.0", inclusive = false) double weightKg) {}

    private final QuoteService quotes;
    private final PluginManager manager;

    public KernelController(QuoteService quotes, PluginManager manager) {
        this.quotes = quotes;
        this.manager = manager;
    }

    @PostMapping("/quotes")
    public QuoteService.Result quote(@Valid @RequestBody QuoteBody b) {
        return quotes.quote(new QuoteRequest(b.originZip(), b.destinationZip(), b.weightKg()));
    }

    @GetMapping("/plugins")
    public Map<String, List<?>> plugins() {
        return Map.of("active", manager.info(), "failed", manager.failures());
    }
}
