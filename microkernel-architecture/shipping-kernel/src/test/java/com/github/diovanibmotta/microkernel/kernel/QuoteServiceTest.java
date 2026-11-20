package com.github.diovanibmotta.microkernel.kernel;

import static com.github.diovanibmotta.microkernel.kernel.TestPlugins.buildJar;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuoteServiceTest {

    @TempDir Path dir;

    private PluginManager manager;

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    private QuoteService serviceWith(Duration timeout) {
        var props = new KernelProperties(dir, Duration.ofHours(1), timeout);
        manager = new PluginManager(props);
        manager.scan();
        return new QuoteService(manager, props);
    }

    private static String quote(String price) {
        return "return List.of(new Quote(\"c\", \"s\", new BigDecimal(\"" + price + "\"), 2));";
    }

    @Test
    void aggregatesAllPluginsSortedByPrice() throws Exception {
        buildJar(dir, "a.jar", "dear", "1", quote("30"));
        buildJar(dir, "b.jar", "cheap", "1", quote("10"));
        var result = serviceWith(Duration.ofSeconds(2)).quote(new QuoteRequest("01000", "02000", 1));
        assertThat(result.quotes()).extracting(QuoteService.PluginQuote::pluginId).containsExactly("cheap", "dear");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void failingPluginIsIsolated() throws Exception {
        buildJar(dir, "ok.jar", "ok", "1", quote("10"));
        buildJar(dir, "boom.jar", "boom", "1", "throw new IllegalStateException(\"boom\");");
        var result = serviceWith(Duration.ofSeconds(2)).quote(new QuoteRequest("01000", "02000", 1));
        assertThat(result.quotes()).extracting(QuoteService.PluginQuote::pluginId).containsExactly("ok");
        assertThat(result.failures()).singleElement().satisfies(f -> {
            assertThat(f.pluginId()).isEqualTo("boom");
            assertThat(f.reason()).contains("boom");
        });
    }

    @Test
    void slowPluginTimesOutWithoutBlockingOthers() throws Exception {
        buildJar(dir, "ok.jar", "ok", "1", quote("10"));
        buildJar(dir, "slow.jar", "slow", "1",
                "try { Thread.sleep(10_000); } catch (InterruptedException e) { throw new RuntimeException(e); } return List.of();");
        long start = System.nanoTime();
        var result = serviceWith(Duration.ofMillis(300)).quote(new QuoteRequest("01000", "02000", 1));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(result.quotes()).extracting(QuoteService.PluginQuote::pluginId).containsExactly("ok");
        assertThat(result.failures()).singleElement().satisfies(f -> assertThat(f.pluginId()).isEqualTo("slow"));
    }
}
