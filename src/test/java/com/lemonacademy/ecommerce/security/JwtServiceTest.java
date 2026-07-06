package com.lemonacademy.ecommerce.security;

import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private User user;
    private final String secretKey = "supersecretkeywhichmustbeatleast32byteslongforhs256";
    private final long jwtExpiration = 1000 * 60 * 60 * 24; // 1 day

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", jwtExpiration);

        user = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo(user.getEmail());
    }

    @Test
    void testGenerateTokenWithExtraClaims() {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("customClaim", "customValue");

        String token = jwtService.generateToken(extraClaims, user);

        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo(user.getEmail());

        String customClaim = jwtService.extractClaim(token, claims -> claims.get("customClaim", String.class));
        assertThat(customClaim).isEqualTo("customValue");
    }

    @Test
    void testIsTokenValid() {
        String token = jwtService.generateToken(user);
        boolean isValid = jwtService.isTokenValid(token, user);

        assertThat(isValid).isTrue();
    }

    @Test
    void testIsTokenValid_WrongUser() {
        String token = jwtService.generateToken(user);

        User wrongUser = User.builder()
                .email("wrong@example.com")
                .build();

        boolean isValid = jwtService.isTokenValid(token, wrongUser);

        assertThat(isValid).isFalse();
    }

    @Test
    void testTokenExpiration() {
        // Set a very short expiration for testing
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L); // Expired 1 second ago

        String token = jwtService.generateToken(user);

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            jwtService.isTokenValid(token, user);
        });
    }

    @Test
    void testExtractClaim() {
        String token = jwtService.generateToken(user);

        String roleClaim = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
        String nameClaim = jwtService.extractClaim(token, claims -> claims.get("name", String.class));

        assertThat(roleClaim).isEqualTo(user.getRole().name());
        assertThat(nameClaim).isEqualTo(user.getName());
    }
}
