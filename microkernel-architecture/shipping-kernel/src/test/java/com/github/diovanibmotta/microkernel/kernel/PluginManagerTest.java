package com.github.diovanibmotta.microkernel.kernel;

import static com.github.diovanibmotta.microkernel.kernel.TestPlugins.ONE_QUOTE_BODY;
import static com.github.diovanibmotta.microkernel.kernel.TestPlugins.buildJar;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.diovanibmotta.microkernel.kernel.PluginManager.PluginInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManagerTest {

    @TempDir Path dir;
    PluginManager manager;

    @BeforeEach
    void setUp() {
        manager = new PluginManager(new KernelProperties(dir, Duration.ofHours(1), Duration.ofSeconds(1)));
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void loadsJarDroppedIntoDirectory() throws Exception {
        buildJar(dir, "a.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        manager.scan();
        assertThat(manager.info()).extracting(PluginInfo::id).containsExactly("alpha");
    }

    @Test
    void sameClassNameInTwoJarsIsIsolated() throws Exception {
        buildJar(dir, "a.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        buildJar(dir, "b.jar", "beta", "1.0", ONE_QUOTE_BODY);
        manager.scan();
        var plugins = manager.plugins();
        assertThat(plugins).hasSize(2);
        assertThat(plugins.get(0).getClass().getName()).isEqualTo(plugins.get(1).getClass().getName());
        assertThat(plugins.get(0).getClass()).isNotEqualTo(plugins.get(1).getClass());
    }

    @Test
    void reloadsWhenJarIsReplaced() throws Exception {
        Path jar = buildJar(dir, "a.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        manager.scan();
        buildJar(dir, "a.jar", "alpha", "2.0", ONE_QUOTE_BODY);
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 10_000));
        manager.scan();
        assertThat(manager.info()).extracting(PluginInfo::version).containsExactly("2.0");
    }

    @Test
    void unloadsWhenJarIsRemoved() throws Exception {
        Path jar = buildJar(dir, "a.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        manager.scan();
        Files.delete(jar);
        manager.scan();
        assertThat(manager.plugins()).isEmpty();
    }

    @Test
    void brokenJarIsReportedAndDoesNotAffectHealthyPlugins() throws Exception {
        buildJar(dir, "good.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        Files.writeString(dir.resolve("bad.jar"), "not a jar");
        manager.scan();
        assertThat(manager.info()).extracting(PluginInfo::id).containsExactly("alpha");
        assertThat(manager.failures()).extracting(PluginManager.Failure::jar).containsExactly("bad.jar");
    }

    @Test
    void duplicatePluginIdIsRejected() throws Exception {
        buildJar(dir, "a.jar", "alpha", "1.0", ONE_QUOTE_BODY);
        buildJar(dir, "b.jar", "alpha", "2.0", ONE_QUOTE_BODY);
        manager.scan();
        assertThat(manager.info()).extracting(PluginInfo::jar).containsExactly("a.jar");
        assertThat(manager.failures()).extracting(PluginManager.Failure::jar).containsExactly("b.jar");
    }
}
