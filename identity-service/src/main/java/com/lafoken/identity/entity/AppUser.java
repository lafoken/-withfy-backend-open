package com.lafoken.identity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_users")
public class AppUser {
    @Id
    private UUID id;
    private String email;
    private String hashedPassword;
    private String fullName;
    private boolean isActive;
    private boolean isEmailVerified;
    private AuthProvider authProvider;
    private String roles;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public void addRole(String roleToAdd) {
        if (roleToAdd == null || roleToAdd.isBlank()) {
            return;
        }
        List<String> currentRoles;
        if (this.roles == null || this.roles.isBlank()) {
            currentRoles = new ArrayList<>();
        } else {
            currentRoles = new ArrayList<>(Arrays.asList(this.roles.split(",")));
            currentRoles = currentRoles.stream().map(String::trim).filter(r -> !r.isEmpty()).collect(Collectors.toList());
        }

        if (!currentRoles.contains(roleToAdd.trim())) {
            currentRoles.add(roleToAdd.trim());
            this.roles = String.join(",", currentRoles);
        }
    }

    public void removeRole(String roleToRemove) {
        if (roleToRemove == null || roleToRemove.isBlank() || this.roles == null || this.roles.isBlank()) {
            return;
        }
        List<String> currentRoles = new ArrayList<>(Arrays.asList(this.roles.split(",")));
        currentRoles = currentRoles.stream().map(String::trim).filter(r -> !r.isEmpty()).collect(Collectors.toList());

        if (currentRoles.remove(roleToRemove.trim())) {
            this.roles = String.join(",", currentRoles);
            if (this.roles.isEmpty()) {
                this.roles = "ROLE_USER";
            }
        }
    }
}
