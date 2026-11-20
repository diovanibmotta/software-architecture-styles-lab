package com.github.diovanibmotta.microkernel.kernel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Compiles a real plug-in from source and packs it as a jar, so tests exercise the real class-loading path. */
final class TestPlugins {

    static final String SPI = "com.github.diovanibmotta.microkernel.api.ShippingPlugin";
    static final String ONE_QUOTE_BODY = "return List.of(new Quote(\"c\", \"s\", new BigDecimal(\"10\"), 2));";

    private TestPlugins() {}

    /** Every plug-in is compiled as class {@code t.P}: same FQCN in different jars proves class-loader isolation. */
    static Path buildJar(Path dir, String jarName, String id, String version, String quoteBody) throws IOException {
        Path work = Files.createTempDirectory(dir, "build-");
        Path src = work.resolve("t/P.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package t;
                import com.github.diovanibmotta.microkernel.api.*;
                import java.math.BigDecimal;
                import java.util.List;
                public class P implements ShippingPlugin {
                    public String id() { return "%s"; }
                    public String version() { return "%s"; }
                    public List<Quote> quote(QuoteRequest r) { %s }
                }
                """.formatted(id, version, quoteBody));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "-cp", System.getProperty("java.class.path"),
                "-d", work.toString(), src.toString());
        if (rc != 0) {
            throw new IllegalStateException("test plug-in failed to compile");
        }

        Path jar = dir.resolve(jarName);
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry("t/P.class"));
            out.write(Files.readAllBytes(work.resolve("t/P.class")));
            out.putNextEntry(new JarEntry("META-INF/services/" + SPI));
            out.write("t.P\n".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }
}
