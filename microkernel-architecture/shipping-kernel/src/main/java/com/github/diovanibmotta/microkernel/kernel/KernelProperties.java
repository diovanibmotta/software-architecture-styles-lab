package com.github.diovanibmotta.microkernel.kernel;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kernel")
public record KernelProperties(Path pluginsDir, Duration scanInterval, Duration pluginTimeout) {}
