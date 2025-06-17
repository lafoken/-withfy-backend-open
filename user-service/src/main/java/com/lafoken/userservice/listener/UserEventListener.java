package com.withfy.userservice.listener;

import com.withfy.userservice.dto.UserBannedEvent;
import com.withfy.userservice.dto.UserRegisteredEvent;
import com.withfy.userservice.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {
    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);
    private final UserProfileService userProfileService;

    public UserEventListener(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.user-registered}")
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: {}", event);
        userProfileService.handleUserRegisteredEvent(event)
            .doOnError(error -> log.error("Error handling UserRegisteredEvent for userId {}: {}", event.userId(), error.getMessage()))
            .subscribe();
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.user-banned}")
    public void onUserBanned(UserBannedEvent event) {
        log.info("Received UserBannedEvent for userId: {}", event.userId());
        userProfileService.handleUserBannedEvent(event.userId())
            .doOnError(error -> log.error("Error handling UserBannedEvent for userId {}: {}", event.userId(), error.getMessage()))
            .subscribe();
    }
}
