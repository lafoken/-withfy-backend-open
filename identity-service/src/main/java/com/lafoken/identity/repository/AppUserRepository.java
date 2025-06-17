package com.lafoken.identity.repository;

import com.lafoken.identity.entity.AppUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface AppUserRepository extends ReactiveCrudRepository<AppUser, UUID> {
    Mono<AppUser> findByEmail(String email);
    Mono<Boolean> existsByEmail(String email);

    @Query("SELECT id, email, full_name, is_active, is_email_verified, auth_provider, roles, created_at, updated_at FROM app_users ORDER BY created_at DESC")
    Flux<AppUser> findAllUsersWithDetails();

    @Query("SELECT id, email, full_name, is_active, is_email_verified, auth_provider, roles, created_at, updated_at FROM app_users ORDER BY created_at DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<AppUser> findAllUsersPaged(Pageable pageable);

    @Query("SELECT COUNT(*) FROM app_users")
    Mono<Long> countAllUsers();

    @Query("INSERT INTO app_users (id, email, hashed_password, full_name, is_active, is_email_verified, auth_provider, roles, created_at, updated_at) " +
           "VALUES (:#{#appUser.id}, :#{#appUser.email}, :#{#appUser.hashedPassword}, :#{#appUser.fullName}, " +
           ":#{#appUser.isActive}, :#{#appUser.isEmailVerified}, :#{#appUser.authProvider.name()}, :#{#appUser.roles}, " +
           ":#{#appUser.createdAt}, :#{#appUser.updatedAt})")
    Mono<Void> insertProfile(AppUser appUser);

    @Query("SELECT EXISTS (SELECT 1 FROM app_users WHERE roles LIKE '%' || :roleName || '%')")
    Mono<Boolean> hasUserWithRole(String roleName);
}
