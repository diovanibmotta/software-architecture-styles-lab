package com.github.diovanibmotta.microservices.delivery;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

interface DeliveryRepository extends CrudRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(long orderId);

    long countByStatus(Delivery.Status status);
}
