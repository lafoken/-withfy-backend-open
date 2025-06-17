package com.lafoken.identity.service;

import reactor.core.publisher.Mono;

public interface EmailService {
    Mono<Void> sendPasswordResetEmail(String to, String token, String username);
    Mono<Void> sendEmailVerificationEmail(String to, String token, String username);
}
