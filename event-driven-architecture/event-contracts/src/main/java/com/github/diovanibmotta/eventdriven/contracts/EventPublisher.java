package com.github.diovanibmotta.eventdriven.contracts;

import org.springframework.jms.core.JmsTemplate;

/** Fire-and-forget publish of a domain event to its topic. The producer never learns who (if anyone) consumes it. */
public class EventPublisher {

    private final JmsTemplate jms;

    EventPublisher(JmsTemplate jms) {
        this.jms = jms;
    }

    public void publish(Object event) {
        String topic = Events.TOPICS.get(event.getClass());
        if (topic == null) {
            throw new IllegalArgumentException("Not a registered event: " + event.getClass());
        }
        jms.convertAndSend(topic, event);
    }
}
