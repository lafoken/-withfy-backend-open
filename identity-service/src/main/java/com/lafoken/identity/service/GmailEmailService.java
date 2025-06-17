package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@Primary
public class GmailEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailService.class);

    private final JavaMailSender mailSender;
    private final JwtProperties jwtProperties;
    private final String frontendUrl;
    private final String senderEmail;

    public GmailEmailService(JavaMailSender mailSender,
                             JwtProperties jwtProperties,
                             @Value("${frontend.url}") String frontendUrl,
                             @Value("${spring.mail.username}") String senderEmail) {
        this.mailSender = mailSender;
        this.jwtProperties = jwtProperties;
        this.frontendUrl = frontendUrl;
        this.senderEmail = senderEmail;
    }

    private String loadEmailTemplate(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) {
            return String.format("%d hour(s) and %d minute(s)", hours, minutes);
        } else {
            return String.format("%d minute(s)", minutes);
        }
    }

    private Mono<Void> sendEmail(String to, String subject, String htmlBody) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

                helper.setFrom(senderEmail);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                log.info("Email sent successfully to {}", to);
            } catch (MessagingException e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
                throw new RuntimeException("Failed to send email", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendPasswordResetEmail(String to, String token, String username) {
        try {
            String template = loadEmailTemplate("password-reset-email.html");
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String expirationTime = formatDuration(jwtProperties.passwordResetTokenExpirationMs());

            String htmlBody = template
                .replace("{{username}}", username)
                .replace("{{token}}", token)
                .replace("{{resetLink}}", resetLink)
                .replace("{{expirationTime}}", expirationTime);

            return sendEmail(to, "Password Reset Request for Your Withfy Account", htmlBody);
        } catch (IOException e) {
            log.error("Failed to load password reset email template: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to load email template", e));
        }
    }

    @Override
    public Mono<Void> sendEmailVerificationEmail(String to, String token, String username) {
        log.warn("sendEmailVerificationEmail is called. Ensure email verification is fully implemented if used.");
        try {
            String template = loadEmailTemplate("email-verification-email.html");
            String verificationLink = frontendUrl + "/verify-email?token=" + token;
            String expirationTime = formatDuration(jwtProperties.passwordResetTokenExpirationMs());


            String htmlBody = template
                .replace("{{username}}", username)
                .replace("{{token}}", token)
                .replace("{{verificationLink}}", verificationLink)
                .replace("{{expirationTime}}", expirationTime);


            return sendEmail(to, "Verify Your Email Address for Withfy", htmlBody);
        } catch (IOException e) {
            log.error("Failed to load email verification template: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Failed to load email template", e));
        }
    }
}
