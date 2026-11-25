package com.github.diovanibmotta.microservices.payment;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

interface PaymentRepository extends CrudRepository<Payment, Long> {
    Optional<Payment> findByOrderId(long orderId);
}
