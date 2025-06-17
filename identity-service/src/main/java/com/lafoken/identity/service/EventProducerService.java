package com.lafoken.identity.service;

import com.lafoken.identity.event.UserBannedEvent;
import com.lafoken.identity.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EventProducerService {

    private static final Logger log = LoggerFactory.getLogger(EventProducerService.class);
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.user-events}")
    private String userEventsExchange;

    @Value("${app.rabbitmq.routing-key.user-registered}")
    private String userRegisteredRoutingKey;

    @Value("${app.rabbitmq.routing-key.user-banned}")
    private String userBannedRoutingKey;

    public EventProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Sending UserRegisteredEvent: {}", event);
        rabbitTemplate.convertAndSend(userEventsExchange, userRegisteredRoutingKey, event);
    }

    public void sendUserBannedEvent(UserBannedEvent event) {
        log.info("Sending UserBannedEvent: {}", event);
        rabbitTemplate.convertAndSend(userEventsExchange, userBannedRoutingKey, event);
    }
}
