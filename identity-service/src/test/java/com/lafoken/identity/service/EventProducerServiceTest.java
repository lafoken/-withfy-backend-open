package com.lafoken.identity.service;

import com.lafoken.identity.event.UserBannedEvent;
import com.lafoken.identity.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class EventProducerServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventProducerService eventProducerService;

    @Captor
    private ArgumentCaptor<UserRegisteredEvent> userRegisteredEventCaptor;

    @Captor
    private ArgumentCaptor<UserBannedEvent> userBannedEventCaptor;

    private final String USER_EVENTS_EXCHANGE = "user.events.test.exchange";
    private final String USER_REGISTERED_ROUTING_KEY = "user.registered.test";
    private final String USER_BANNED_ROUTING_KEY = "user.banned.test";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventProducerService, "userEventsExchange", USER_EVENTS_EXCHANGE);
        ReflectionTestUtils.setField(eventProducerService, "userRegisteredRoutingKey", USER_REGISTERED_ROUTING_KEY);
        ReflectionTestUtils.setField(eventProducerService, "userBannedRoutingKey", USER_BANNED_ROUTING_KEY);
    }

    @Test
    void sendUserRegisteredEvent_shouldSendCorrectEventToRabbit() {
        UserRegisteredEvent event = new UserRegisteredEvent("userId1", "test@example.com", "Test User", "LOCAL");
        eventProducerService.sendUserRegisteredEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(USER_EVENTS_EXCHANGE),
            eq(USER_REGISTERED_ROUTING_KEY),
            userRegisteredEventCaptor.capture()
        );
        assertEquals(event, userRegisteredEventCaptor.getValue());
    }

    @Test
    void sendUserBannedEvent_shouldSendCorrectEventToRabbit() {
        UserBannedEvent event = new UserBannedEvent("userId2");
        eventProducerService.sendUserBannedEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(USER_EVENTS_EXCHANGE),
            eq(USER_BANNED_ROUTING_KEY),
            userBannedEventCaptor.capture()
        );
        assertEquals(event, userBannedEventCaptor.getValue());
    }
}
