package com.withfy.userservice.dto;

import com.withfy.userservice.entity.UserProfile;
import org.springframework.util.StringUtils;

public record UserProfileResponse(
    String id,
    String email,
    String fullName,
    String avatarUrl,
    String billingAddress,
    String paymentMethod
) {
    public static UserProfileResponse fromEntity(UserProfile entity) {
        String finalAvatarUrl = entity.getAvatarUrl();

        return new UserProfileResponse(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getEmail(),
            entity.getFullName(),
            finalAvatarUrl,
            entity.getBillingAddress(),
            entity.getPaymentMethod()
        );
    }
}
