package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenProviderTest {

    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private JwtProperties jwtProperties;

    @InjectMocks
    private TokenProvider tokenProvider;

    private final String TEST_SECRET = "93BAK5WP3UgdXa3hTQIC6OoxwUxRZeahST5OU5HQiHHWwWPz3Me3yJkeb/FUx6jnS/V/PBsfp+AhruMi7W70+Q==";
    private final long ACCESS_TOKEN_EXPIRATION_MS = 3600000L;


    @BeforeEach
    void setUp() {
        when(jwtProperties.secret()).thenReturn(TEST_SECRET);
        when(jwtProperties.accessTokenExpirationMs()).thenReturn(ACCESS_TOKEN_EXPIRATION_MS);
        tokenProvider.init();
    }

    @Test
    void validateToken_withMalformedToken_shouldReturnFalse() {
        assertFalse(tokenProvider.validateToken("this.is.not.a.jwt"));
    }

    @Test
    void validateToken_withUnsupportedToken_shouldLogAndReturnFalse() {
        assertFalse(tokenProvider.validateToken(" "));
    }
}
