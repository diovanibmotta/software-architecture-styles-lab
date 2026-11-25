package com.github.diovanibmotta.microservices.order;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

interface OutboxRepository extends CrudRepository<OutboxMessage, Long> {
    List<OutboxMessage> findBySentFalseOrderById();
}
