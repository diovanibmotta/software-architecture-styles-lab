package com.github.diovanibmotta.microservices.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.StreamSupport;

@RestController
class ItemController {

    record NewItem(@NotBlank String name, @Positive long priceCents) {
    }

    private final ItemRepository items;

    ItemController(ItemRepository items) {
        this.items = items;
    }

    @PostMapping("/items")
    ResponseEntity<Item> create(@Valid @RequestBody NewItem body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(items.save(new Item(null, body.name(), body.priceCents())));
    }

    @GetMapping("/items")
    List<Item> list() {
        return StreamSupport.stream(items.findAll().spliterator(), false).toList();
    }

    @GetMapping("/items/{id}")
    ResponseEntity<Item> get(@PathVariable long id) {
        return items.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
