package com.lafoken.identity.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    String userId
) {}
