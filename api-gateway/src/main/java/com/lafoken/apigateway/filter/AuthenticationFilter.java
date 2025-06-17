package com.withfy.apigateway.filter;

import com.withfy.apigateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private final JwtProperties jwtProperties;
    private SecretKey key;

    public AuthenticationFilter(JwtProperties jwtProperties) {
        super(Config.class);
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public static class Config {
    }

    private boolean isSecured(ServerHttpRequest request) {
        final List<String> openApiEndpoints = List.of(
            "/api/v1/identity/auth/register",
            "/api/v1/identity/auth/login",
            "/api/v1/identity/auth/refresh",
            "/api/v1/identity/auth/forgot-password",
            "/api/v1/identity/auth/reset-password",
            "/oauth2/authorization/google",
            "/login/oauth2/code/google",
            "/api/v1/identity/admin/init-fixed-admin"
        );

        String path = request.getURI().getPath();
        log.debug("Checking security for path: {}", path);

        for (String openEndpoint : openApiEndpoints) {
            if (path.equals(openEndpoint)) {
                log.debug("Path {} is an open endpoint.", path);
                return false;
            }
        }

        if (path.startsWith("/api/v1/identity/") ||
            path.startsWith("/api/v1/user/") ||
            path.startsWith("/api/v1/song/") ||
            path.startsWith("/api/v1/playlist/") ||
            path.startsWith("/api/v1/billing/")) {
            log.debug("Path {} is a potentially secured API endpoint.", path);
            return true;
        }

        log.debug("Path {} is not considered secured by this filter's rules.", path);
        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    private String getAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return getAuthHeader(request) == null;
    }

    private String getTokenFromHeader(ServerHttpRequest request) {
        String header = getAuthHeader(request);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean isTokenInvalid(String token) {
         try {
            Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token);
            return false;
        } catch (ExpiredJwtException | MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            return true;
        }
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token).getPayload();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            log.info("AuthenticationFilter: Processing request to {} {}", request.getMethod(), request.getURI().getPath());

            if (request.getMethod() == HttpMethod.OPTIONS) {
                log.info("AuthenticationFilter: Allowing OPTIONS preflight request for {}. Skipping authentication.", request.getURI().getPath());
                return chain.filter(exchange);
            }

            if (this.isSecured(request)) {
                log.info("AuthenticationFilter: Path {} is SECURED", request.getURI().getPath());
                if (this.isAuthMissing(request)) {
                    log.warn("AuthenticationFilter: Authorization header is missing for {}", request.getURI().getPath());
                    return this.onError(exchange, HttpStatus.UNAUTHORIZED);
                }

                final String token = this.getTokenFromHeader(request);

                if (token == null) {
                    log.warn("AuthenticationFilter: Bearer token is missing after header check for {}", request.getURI().getPath());
                    return this.onError(exchange, HttpStatus.UNAUTHORIZED);
                }

                if (this.isTokenInvalid(token)) {
                     log.warn("AuthenticationFilter: Invalid token for {}", request.getURI().getPath());
                    return this.onError(exchange, HttpStatus.UNAUTHORIZED);
                }

                log.info("AuthenticationFilter: Token is valid for {}. Adding user headers.", request.getURI().getPath());
                Claims claims = this.getAllClaimsFromToken(token);
                String roles = claims.get("auth", String.class);
                if (roles == null) {
                    roles = "";
                }

                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-ID", String.valueOf(claims.get("userId", String.class)))
                    .header("X-User-Email", claims.getSubject())
                    .header("X-User-Roles", roles)
                    .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                 log.info("AuthenticationFilter: Path {} is NOT SECURED, passing through.", request.getURI().getPath());
            }
            return chain.filter(exchange);
        };
    }
}
