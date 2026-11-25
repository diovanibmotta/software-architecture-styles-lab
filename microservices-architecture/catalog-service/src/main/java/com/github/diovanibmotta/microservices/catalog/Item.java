package com.github.diovanibmotta.microservices.catalog;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("items")
public record Item(@Id Long id, String name, long priceCents) {
}
