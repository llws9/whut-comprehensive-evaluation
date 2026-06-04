package edu.whut.eval.app.security;

import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenIssuerTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Test
    void shouldIssueAccessAndRefreshTokensWithDifferentClaimShapes() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(SECRET);
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties);
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "class-monitor"),
                Set.of("evaluation:apply:create", "evaluation:record:query"),
                List.of()
        );

        JwtTokenPair tokenPair = issuer.issueTokenPair(currentUser);
        Claims accessClaims = parseClaims(tokenPair.getAccessToken());
        Claims refreshClaims = parseClaims(tokenPair.getRefreshToken());

        assertThat(tokenPair.getAccessToken()).isNotBlank();
        assertThat(tokenPair.getRefreshToken()).isNotBlank();
        assertThat(tokenPair.getAccessTokenExpiresAt()).isBefore(tokenPair.getRefreshTokenExpiresAt());

        assertThat(accessClaims.get("token_type", String.class)).isEqualTo("access");
        assertThat(accessClaims.get("uid", Integer.class)).isEqualTo(1001);
        assertThat(accessClaims.get("uno", String.class)).isEqualTo("2024305999");
        assertThat(accessClaims.get("uname", String.class)).isEqualTo("Test User");
        assertThat(accessClaims.get("identity", String.class)).isEqualTo("student");
        assertThat(accessClaims.get("roles", java.util.List.class)).containsExactlyInAnyOrder("student", "class-monitor");
        assertThat(accessClaims.get("authorities", java.util.List.class)).containsExactlyInAnyOrder(
                "evaluation:apply:create",
                "evaluation:record:query"
        );

        assertThat(refreshClaims.get("token_type", String.class)).isEqualTo("refresh");
        assertThat(refreshClaims.get("uid", Integer.class)).isEqualTo(1001);
        assertThat(refreshClaims.get("uno", String.class)).isEqualTo("2024305999");
        assertThat(refreshClaims.get("identity", String.class)).isEqualTo("student");
        assertThat(refreshClaims.get("uname")).isNull();
        assertThat(refreshClaims.get("roles")).isNull();
        assertThat(refreshClaims.get("authorities")).isNull();
    }

    @Test
    void shouldIssueTokenPairWithSameSessionIdClaim() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(SECRET);
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties);
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of()
        );

        JwtTokenPair tokenPair = issuer.issueTokenPair(currentUser, "session-no-123");
        Claims accessClaims = parseClaims(tokenPair.getAccessToken());
        Claims refreshClaims = parseClaims(tokenPair.getRefreshToken());

        assertThat(tokenPair.getSessionNo()).isEqualTo("session-no-123");
        assertThat(tokenPair.getAccessTokenId()).isNotBlank();
        assertThat(tokenPair.getRefreshTokenId()).isNotBlank();
        assertThat(accessClaims.get("sid", String.class)).isEqualTo("session-no-123");
        assertThat(refreshClaims.get("sid", String.class)).isEqualTo("session-no-123");
        assertThat(accessClaims.getId()).isEqualTo(tokenPair.getAccessTokenId());
        assertThat(refreshClaims.getId()).isEqualTo(tokenPair.getRefreshTokenId());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
