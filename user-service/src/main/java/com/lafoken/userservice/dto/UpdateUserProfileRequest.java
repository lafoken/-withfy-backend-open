package com.withfy.userservice.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UpdateUserProfileRequest(
    @Size(max = 255, message = "Full name must be less than 255 characters")
    String fullName,

    @Pattern(regexp = "^$|(https?://.+)", message = "Avatar URL must be a valid HTTP/HTTPS URL or empty")
    @Size(max = 1024, message = "Avatar URL must be less than 1024 characters")
    String avatarUrl
) {}
