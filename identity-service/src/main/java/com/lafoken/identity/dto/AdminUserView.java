package com.lafoken.identity.dto;

import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record AdminUserView(
    String id,
    String email,
    String fullName,
    boolean isActive,
    boolean isEmailVerified,
    String authProvider,
    List<String> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AdminUserView fromEntity(AppUser appUser) {
        List<String> rolesList = appUser.getRoles() != null ?
            Arrays.stream(appUser.getRoles().split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toList())
            : List.of("ROLE_USER");

        return new AdminUserView(
            appUser.getId().toString(),
            appUser.getEmail(),
            appUser.getFullName(),
            appUser.isActive(),
            appUser.isEmailVerified(),
            appUser.getAuthProvider() != null ? appUser.getAuthProvider().name() : AuthProvider.LOCAL.name(),
            rolesList,
            appUser.getCreatedAt(),
            appUser.getUpdatedAt()
        );
    }
}
