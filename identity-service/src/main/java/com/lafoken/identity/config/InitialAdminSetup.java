package com.lafoken.identity.config;

import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class InitialAdminSetup implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminSetup.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@withfy.com";
    private static final String ADMIN_PASSWORD = "Admin2025";
    private static final String ADMIN_FULL_NAME = "Default Admin";
    private static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";
    private static final String USER_ROLE_NAME = "ROLE_USER";


    public InitialAdminSetup(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting initial admin setup check...");

        appUserRepository.existsByEmail(ADMIN_EMAIL)
            .flatMap(adminExists -> {
                if (adminExists) {
                    log.info("Admin user with email '{}' already exists. Skipping creation.", ADMIN_EMAIL);
                    return Mono.empty();
                } else {
                    log.info("Admin user with email '{}' does not exist. Checking for other admins...", ADMIN_EMAIL);
                    return appUserRepository.hasUserWithRole(ADMIN_ROLE_NAME)
                        .flatMap(otherAdminsExist -> {
                            if (otherAdminsExist) {
                                log.warn("Other admin users exist in the database. Default admin '{}' will not be created automatically. " +
                                         "Please manage admin users manually or ensure this is the first setup.", ADMIN_EMAIL);
                                return Mono.empty();
                            } else {
                                log.info("No other admin users found. Proceeding to create default admin '{}'.", ADMIN_EMAIL);
                                AppUser adminUser = AppUser.builder()
                                    .id(UUID.randomUUID())
                                    .email(ADMIN_EMAIL)
                                    .hashedPassword(passwordEncoder.encode(ADMIN_PASSWORD))
                                    .fullName(ADMIN_FULL_NAME)
                                    .isActive(true)
                                    .isEmailVerified(true)
                                    .authProvider(AuthProvider.LOCAL)
                                    .roles(String.join(",", ADMIN_ROLE_NAME, USER_ROLE_NAME))
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();

                                return appUserRepository.insertProfile(adminUser)
                                    .then(Mono.defer(() -> appUserRepository.findByEmail(ADMIN_EMAIL)))
                                    .doOnSuccess(savedAdmin -> {
                                        if (savedAdmin != null) {
                                            log.info("Default admin user '{}' created successfully with ID: {}", savedAdmin.getEmail(), savedAdmin.getId());
                                        } else {
                                            log.warn("Default admin user '{}' was inserted, but could not be immediately retrieved for logging ID.", ADMIN_EMAIL);
                                        }
                                    })
                                    .doOnError(error -> log.error("Error creating default admin user '{}': {}", ADMIN_EMAIL, error.getMessage()))
                                    .then();
                            }
                        });
                }
            })
            .subscribe(
                null,
                error -> log.error("An error occurred during initial admin setup: {}", error.getMessage()),
                () -> log.info("Initial admin setup check completed.")
            );
    }
}
