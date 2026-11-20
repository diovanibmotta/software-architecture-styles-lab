package com.github.diovanibmotta.microkernel.kernel;

import com.github.diovanibmotta.microkernel.api.ShippingPlugin;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Plug-in registry + loader. Each jar gets its own class loader (parent = the kernel's, so the API is shared),
 * which is what makes hot load / unload / reload possible and keeps plug-in classes isolated from each other.
 */
@Component
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    public record PluginInfo(String id, String version, String jar) {}

    public record Failure(String jar, String reason) {}

    private record Loaded(long lastModified, URLClassLoader loader, Path copy, List<ShippingPlugin> plugins) {}

    private final Path dir;
    private final Map<String, Loaded> loaded = new ConcurrentHashMap<>();
    private final Map<String, Failure> failed = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

    public PluginManager(KernelProperties props) {
        this.dir = props.pluginsDir();
    }

    @Scheduled(fixedDelayString = "${kernel.scan-interval}")
    public synchronized void scan() {
        try {
            Files.createDirectories(dir);
            Set<String> present = new HashSet<>();
            try (Stream<Path> jars = Files.list(dir)) {
                for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                    String name = jar.getFileName().toString();
                    present.add(name);
                    long modified = Files.getLastModifiedTime(jar).toMillis();
                    Loaded current = loaded.get(name);
                    boolean changed = current != null && current.lastModified() != modified;
                    boolean retryFailed = current == null && !Long.valueOf(modified).equals(failedAt.get(name));
                    if (changed) {
                        unload(name);
                    }
                    if (changed || retryFailed) {
                        load(name, jar, modified);
                    }
                }
            }
            new ArrayList<>(loaded.keySet()).stream().filter(n -> !present.contains(n)).forEach(this::unload);
            failed.keySet().removeIf(n -> !present.contains(n));
            failedAt.keySet().removeIf(n -> !present.contains(n));
        } catch (IOException e) {
            log.error("Plug-in scan failed", e);
        }
    }

    private void load(String name, Path jar, long modified) {
        URLClassLoader loader = null;
        Path copy = null;
        try {
            // Load from a private copy: Windows locks jars opened by a class loader, which would block
            // deleting/replacing the original (the whole point of hot deploy).
            copy = Files.createTempFile("plugin-", ".jar");
            Files.copy(jar, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            loader = new URLClassLoader(new URL[] {copy.toUri().toURL()}, ShippingPlugin.class.getClassLoader());
            List<ShippingPlugin> found = ServiceLoader.load(ShippingPlugin.class, loader).stream()
                    .map(ServiceLoader.Provider::get).toList();
            if (found.isEmpty()) {
                throw new IllegalStateException("no ShippingPlugin service found in jar");
            }
            Set<String> taken = new HashSet<>(plugins().stream().map(ShippingPlugin::id).toList());
            for (ShippingPlugin p : found) {
                if (!taken.add(p.id())) {
                    throw new IllegalStateException("duplicate plug-in id '" + p.id() + "'");
                }
            }
            List<ShippingPlugin> started = new ArrayList<>();
            try {
                for (ShippingPlugin p : found) {
                    p.start();
                    started.add(p);
                }
            } catch (Throwable t) {
                started.forEach(this::stopQuietly);
                throw t;
            }
            loaded.put(name, new Loaded(modified, loader, copy, List.copyOf(found)));
            failed.remove(name);
            failedAt.remove(name);
            log.info("Loaded plug-in(s) {} from {}", found.stream().map(ShippingPlugin::id).toList(), name);
        } catch (Exception | LinkageError | ServiceConfigurationError e) {
            closeQuietly(loader);
            deleteQuietly(copy);
            failed.put(name, new Failure(name, e.toString()));
            failedAt.put(name, modified);
            log.warn("Could not load plug-in jar {}: {}", name, e.toString());
        }
    }

    private void unload(String name) {
        Loaded l = loaded.remove(name);
        if (l == null) {
            return;
        }
        l.plugins().forEach(this::stopQuietly);
        closeQuietly(l.loader());
        deleteQuietly(l.copy());
        log.info("Unloaded plug-in(s) from {}", name);
    }

    @PreDestroy
    public synchronized void shutdown() {
        new ArrayList<>(loaded.keySet()).forEach(this::unload);
    }

    private void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                file.toFile().deleteOnExit();
            }
        }
    }

    private void stopQuietly(ShippingPlugin p) {
        try {
            p.stop();
        } catch (Throwable t) {
            log.warn("Plug-in {} failed to stop cleanly: {}", p.id(), t.toString());
        }
    }

    private void closeQuietly(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // nothing useful to do; the jar handle is released on GC at worst
            }
        }
    }

    /** Snapshot of active plug-ins. */
    public List<ShippingPlugin> plugins() {
        return loaded.values().stream().flatMap(l -> l.plugins().stream()).toList();
    }

    public List<PluginInfo> info() {
        Map<String, PluginInfo> sorted = new TreeMap<>();
        loaded.forEach((jar, l) -> l.plugins().forEach(p -> sorted.put(p.id(), new PluginInfo(p.id(), p.version(), jar))));
        return List.copyOf(sorted.values());
    }

    public List<Failure> failures() {
        return failed.values().stream().sorted(java.util.Comparator.comparing(Failure::jar)).toList();
    }
}
