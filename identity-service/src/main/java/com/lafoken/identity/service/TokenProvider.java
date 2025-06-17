package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);
    private static final String AUTHORITIES_KEY = "auth";
    private static final String USER_ID_KEY = "userId";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    public TokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String email, String userId, Collection<? extends GrantedAuthority> authoritiesCol) {
        log.info("TokenProvider.createAccessToken: Received authorities for email {}: {}", email, authoritiesCol);
        String authorities = authoritiesCol.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        log.info("TokenProvider.createAccessToken: Authorities string to be put in token for email {}: '{}'", email, authorities);
        long now = (new Date()).getTime();
        Date validity = new Date(now + jwtProperties.accessTokenExpirationMs());

        return Jwts.builder()
            .subject(email)
            .claim(AUTHORITIES_KEY, authorities)
            .claim(USER_ID_KEY, userId)
            .signWith(key, Jwts.SIG.HS512)
            .expiration(validity)
            .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token).getPayload();
        return claims.get(USER_ID_KEY, String.class);
    }

    public Collection<? extends GrantedAuthority> getAuthoritiesFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token).getPayload();
        String authoritiesString = claims.get(AUTHORITIES_KEY, String.class);
        if (authoritiesString == null || authoritiesString.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(authoritiesString.split(","))
            .map(String::trim)
            .filter(auth -> !auth.isEmpty())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(this.key).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException e) {
            log.info("Invalid JWT signature.");
            log.trace("Invalid JWT signature trace:", e);
        } catch (MalformedJwtException e) {
            log.info("Invalid JWT token detected (MalformedJwtException).");
            log.trace("Malformed JWT token trace:", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token.");
            log.trace("Expired JWT token trace:", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token.");
            log.trace("Unsupported JWT token trace:", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty or invalid (IllegalArgumentException).");
            log.trace("JWT claims string invalid trace:", e);
        }
        return false;
    }
}
