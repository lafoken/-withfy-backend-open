package com.lafoken.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserRegistrationResponse(
    String userId,
    String email,
    String fullName,
    String message
) {}
