package edu.whut.eval.app.security;

import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.jwt.RefreshTokenSubject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenClaimsMapperTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Test
    void shouldMapSessionAndRefreshTokenIdsFromRefreshClaims() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(SECRET);
        RefreshTokenClaimsMapper mapper = new RefreshTokenClaimsMapper(properties);
        Claims claims = parseRefreshClaims();

        RefreshTokenSubject subject = mapper.map(claims);

        assertThat(subject.getUserId()).isEqualTo(1001L);
        assertThat(subject.getUserNo()).isEqualTo("2024305999");
        assertThat(subject.getIdentity()).isEqualTo("student");
        assertThat(subject.getSessionNo()).isEqualTo("session-no-123");
        assertThat(subject.getRefreshTokenId()).isEqualTo("refresh-jti-456");
    }

    private Claims parseRefreshClaims() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .id("refresh-jti-456")
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "refresh")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
