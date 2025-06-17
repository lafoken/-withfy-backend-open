package com.lafoken.identity.event;

import java.io.Serializable;

public record UserBannedEvent(String userId) implements Serializable {}
