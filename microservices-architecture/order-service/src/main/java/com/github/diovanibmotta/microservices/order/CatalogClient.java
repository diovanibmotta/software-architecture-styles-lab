package com.github.diovanibmotta.microservices.order;

import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;

/** Order's own view of the catalog: only the fields it needs, not a shared class. */
public class CatalogClient {

    public record Item(long id, String name, long priceCents) {
    }

    private final Downstream downstream;

    public CatalogClient(Downstream downstream) {
        this.downstream = downstream;
    }

    public Optional<Item> find(long itemId) {
        try {
            return Optional.ofNullable(downstream.call(c -> c.get().uri("/items/{id}", itemId).retrieve().body(Item.class)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
