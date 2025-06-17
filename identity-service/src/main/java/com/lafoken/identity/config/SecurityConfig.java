package com.lafoken.identity.config;

import com.lafoken.identity.security.OAuth2AuthenticationSuccessHandler;
import com.lafoken.identity.service.AppUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    public SecurityConfig(AppUserDetailsService appUserDetailsService,
                          OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler) {
        this.appUserDetailsService = appUserDetailsService;
        this.oauth2AuthenticationSuccessHandler = oauth2AuthenticationSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public ReactiveAuthenticationManager userDetailsAuthenticationManager(PasswordEncoder passwordEncoder) {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager =
                new UserDetailsRepositoryReactiveAuthenticationManager(appUserDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }

    @Bean
    public WebFilter preAuthenticatedProcessingFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String userIdHeader = request.getHeaders().getFirst("X-User-ID");
            String emailHeader = request.getHeaders().getFirst("X-User-Email");
            String rolesHeader = request.getHeaders().getFirst("X-User-Roles");

            if (userIdHeader != null && !userIdHeader.isBlank() && emailHeader != null && !emailHeader.isBlank()) {
                List<SimpleGrantedAuthority> authorities = rolesHeader != null && !rolesHeader.isBlank()
                    ? Arrays.stream(rolesHeader.split(","))
                            .filter(role -> !role.trim().isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList())
                    : Collections.emptyList();

                User principal = new User(emailHeader, "", authorities);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            }
            return chain.filter(exchange);
        };
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .addFilterAt(preAuthenticatedProcessingFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/api/v1/identity/auth/register").permitAll()
                .pathMatchers("/api/v1/identity/auth/login").permitAll()
                .pathMatchers("/api/v1/identity/auth/refresh").permitAll()
                .pathMatchers("/api/v1/identity/auth/forgot-password").permitAll()
                .pathMatchers("/api/v1/identity/admin/init-fixed-admin").permitAll()
                .pathMatchers("/api/v1/identity/auth/reset-password").permitAll()
                .pathMatchers("/api/v1/identity/admin/check-admin-role").authenticated()
                .pathMatchers("/api/v1/identity/admin/**").hasRole("ADMIN")
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/login/oauth2/code/**").permitAll()
                .pathMatchers("/oauth2/authorization/**").permitAll()
                .anyExchange().authenticated()
            )
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })
                .accessDeniedHandler(new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN))
            )
            .oauth2Login(oauth2 ->
                oauth2.authenticationSuccessHandler(oauth2AuthenticationSuccessHandler)
            );
        return http.build();
    }
}
