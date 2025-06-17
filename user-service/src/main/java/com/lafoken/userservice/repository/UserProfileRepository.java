package com.withfy.userservice.repository;

import com.withfy.userservice.entity.UserProfile;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends ReactiveCrudRepository<UserProfile, UUID> {
    Mono<UserProfile> findByEmail(String email);

    @Query("INSERT INTO user_profiles (id, email, full_name, avatar_url, billing_address, payment_method, created_at, updated_at) " +
           "VALUES (:#{#userProfile.id}, :#{#userProfile.email}, :#{#userProfile.fullName}, :#{#userProfile.avatarUrl}, " +
           ":#{#userProfile.billingAddress}, :#{#userProfile.paymentMethod}, :#{#userProfile.createdAt}, :#{#userProfile.updatedAt})")
    Mono<Void> insertProfile(UserProfile userProfile);

    @Modifying
    @Query("UPDATE user_profiles SET full_name = :fullName, updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> updateFullNameAndTimestamp(@Param("id") UUID id, @Param("fullName") String fullName, @Param("updatedAt") LocalDateTime updatedAt);
    @Modifying
    @Query("UPDATE user_profiles SET " +
           "full_name = COALESCE(:#{#request.fullName}, full_name), " +
           "avatar_url = COALESCE(:#{#request.avatarUrl}, avatar_url), " +
           "updated_at = :updatedAt " +
           "WHERE id = :id")
    Mono<Integer> updateProfile(@Param("id") UUID id, @Param("request") com.withfy.userservice.dto.UpdateUserProfileRequest request, @Param("updatedAt") LocalDateTime updatedAt);
}

