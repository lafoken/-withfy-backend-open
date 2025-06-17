package com.lafoken.identity.event;

import java.io.Serializable;

public record UserRegisteredEvent(
    String userId,
    String email,
    String fullName,
    String authProvider
) implements Serializable {}

