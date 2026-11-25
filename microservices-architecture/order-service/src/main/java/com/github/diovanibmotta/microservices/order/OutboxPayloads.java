package com.github.diovanibmotta.microservices.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class OutboxPayloads {

    private final ObjectMapper json;

    OutboxPayloads(ObjectMapper json) {
        this.json = json;
    }

    String dispatch(Order order) {
        return write(Map.of("orderId", order.id(), "address", order.address()));
    }

    /** For REFUND the payload carries the payment id to compensate. */
    String refund(Order order) {
        return write(Map.of("paymentId", order.paymentId()));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    long paymentId(OutboxMessage message) {
        try {
            return json.readTree(message.payload()).get("paymentId").asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
