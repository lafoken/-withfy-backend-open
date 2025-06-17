package com.withfy.userservice.dto;

import java.io.Serializable;

public record UserRegisteredEvent(
    String userId,
    String email,
    String fullName,
    String authProvider
) implements Serializable {}

