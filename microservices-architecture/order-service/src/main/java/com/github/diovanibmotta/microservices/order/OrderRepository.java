package com.github.diovanibmotta.microservices.order;

import org.springframework.data.repository.CrudRepository;

interface OrderRepository extends CrudRepository<Order, Long> {
}
