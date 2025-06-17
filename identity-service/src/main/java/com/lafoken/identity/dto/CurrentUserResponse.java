package com.lafoken.identity.dto;

import java.util.List;

public record CurrentUserResponse(
    String userId,
    String email,
    String fullName,
    List<String> roles,
    String authProvider
) {}
