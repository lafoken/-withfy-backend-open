package com.withfy.userservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange.user-events}")
    private String userEventsExchangeName;

    @Value("${app.rabbitmq.queue.user-registered}")
    private String userRegisteredQueueName;

    @Value("${app.rabbitmq.routing-key.user-registered}")
    private String userRegisteredRoutingKey;

    @Value("${app.rabbitmq.queue.user-banned}")
    private String userBannedQueueName;

    @Value("${app.rabbitmq.routing-key.user-banned}")
    private String userBannedRoutingKey;

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(userEventsExchangeName);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(userRegisteredQueueName, true);
    }

    @Bean
    public Queue userBannedQueue() {
        return new Queue(userBannedQueueName, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(userEventsExchange).with(userRegisteredRoutingKey);
    }

    @Bean
    public Binding userBannedBinding(Queue userBannedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userBannedQueue).to(userEventsExchange).with(userBannedRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter consumerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
