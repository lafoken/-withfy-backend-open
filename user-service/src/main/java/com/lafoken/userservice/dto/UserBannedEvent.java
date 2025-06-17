package com.withfy.userservice.dto;

import java.io.Serializable;

public record UserBannedEvent(String userId) implements Serializable {}
