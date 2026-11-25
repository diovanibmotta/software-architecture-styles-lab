package com.github.diovanibmotta.microservices.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

/** Bounded context "Catalog": what can be ordered and for how much. Owns the {@code items} table, nothing else. */
@SpringBootApplication
@PropertySource("classpath:catalog.properties")
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
