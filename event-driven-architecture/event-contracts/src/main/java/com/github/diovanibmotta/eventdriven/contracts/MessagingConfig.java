package com.github.diovanibmotta.eventdriven.contracts;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.HashMap;
import java.util.Map;

/** JMS wiring shared by all processors: JSON messages, topics, durable *shared* subscriptions. */
@Configuration
@EnableJms
public class MessagingConfig {

    /** Name of the listener container factory to use in {@code @JmsListener(containerFactory = ...)}. */
    public static final String TOPIC_FACTORY = "topicListenerFactory";

    @Bean
    MessageConverter jmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        Map<String, Class<?>> ids = new HashMap<>();
        Events.TOPICS.keySet().forEach(type -> ids.put(type.getSimpleName(), type));
        converter.setTypeIdMappings(ids);
        return converter;
    }

    @Bean
    EventPublisher eventPublisher(ConnectionFactory connectionFactory, MessageConverter converter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(true);
        template.setMessageConverter(converter);
        return new EventPublisher(template);
    }

    /**
     * Durable + shared subscription: messages published while a processor is down wait for it in the broker, and
     * several instances with the same subscription name compete for messages (scale-out without double processing).
     */
    @Bean(TOPIC_FACTORY)
    DefaultJmsListenerContainerFactory topicListenerFactory(ConnectionFactory connectionFactory,
                                                            MessageConverter converter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setPubSubDomain(true);
        factory.setSubscriptionDurable(true);
        factory.setSubscriptionShared(true);
        return factory;
    }
}
