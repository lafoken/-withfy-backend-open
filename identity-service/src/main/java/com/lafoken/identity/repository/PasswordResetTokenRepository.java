package com.lafoken.identity.repository;

import com.lafoken.identity.entity.PasswordResetToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken, UUID> {
    Mono<PasswordResetToken> findByToken(String token);
    Mono<Void> deleteByToken(String token);
}
