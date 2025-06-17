package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private JwtProperties jwtProperties;

    @InjectMocks
    private GmailEmailService emailService;

    private final String FRONTEND_URL = "http://test.frontend.com";
    private final String SENDER_EMAIL = "noreply@example.com";
    private final long TOKEN_EXPIRATION_MS = 3600000L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
        ReflectionTestUtils.setField(emailService, "senderEmail", SENDER_EMAIL);
        lenient().when(jwtProperties.passwordResetTokenExpirationMs()).thenReturn(TOKEN_EXPIRATION_MS);

        MimeMessage mimeMessageMock = mock(MimeMessage.class);
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);
    }

    @Test
    void sendPasswordResetEmail_shouldConstructAndSendEmail() throws Exception {
        String to = "user@example.com";
        String token = "reset-token-123";
        String username = "TestUser";

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        doNothing().when(mailSender).send(messageCaptor.capture());

        Mono<Void> result = emailService.sendPasswordResetEmail(to, token, username);
        StepVerifier.create(result).verifyComplete();

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendEmailVerificationEmail_shouldConstructAndSendEmail() throws Exception {
        String to = "verify@example.com";
        String token = "verify-token-456";
        String username = "Verify User";

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        doNothing().when(mailSender).send(messageCaptor.capture());

        Mono<Void> result = emailService.sendEmailVerificationEmail(to, token, username);
        StepVerifier.create(result).verifyComplete();

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
