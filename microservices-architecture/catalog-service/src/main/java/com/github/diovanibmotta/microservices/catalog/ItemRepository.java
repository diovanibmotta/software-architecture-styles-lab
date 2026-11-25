package com.github.diovanibmotta.microservices.catalog;

import org.springframework.data.repository.CrudRepository;

interface ItemRepository extends CrudRepository<Item, Long> {
}
