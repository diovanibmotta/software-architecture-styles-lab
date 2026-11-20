package com.github.diovanibmotta.microkernel.kernel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class KernelApplication {
    public static void main(String[] args) {
        SpringApplication.run(KernelApplication.class, args);
    }
}
