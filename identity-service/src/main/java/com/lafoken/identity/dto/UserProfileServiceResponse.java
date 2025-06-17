package com.lafoken.identity.dto;

public record UserProfileServiceResponse(
    String id,
    String email,
    String fullName,
    String avatarUrl,
    String billingAddress,
    String paymentMethod
) {}

