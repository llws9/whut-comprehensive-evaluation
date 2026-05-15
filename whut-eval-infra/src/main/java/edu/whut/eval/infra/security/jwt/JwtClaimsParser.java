package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;

@Component
public class JwtClaimsParser {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimsParser.class);

    private final SecurityProperties securityProperties;

    public JwtClaimsParser(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public Claims parse(String token, String source) {
        JwtProperties jwtProperties = securityProperties.getJwt();
        if (!jwtProperties.isEnabled()) {
            AppLog.warn(log, "security.jwt.parse.skipped-disabled",
                    "source", source,
                    "tokenLength", token.length());
            throw new JwtAuthenticationException("JWT support is disabled");
        }

        try {
            AppLog.info(log, "security.jwt.parse.started",
                    "source", source,
                    "algorithm", jwtProperties.getAlgorithm(),
                    "issuer", jwtProperties.getIssuer(),
                    "audience", jwtProperties.getAudience(),
                    "tokenLength", token.length());

            Claims claims = buildParser(jwtProperties)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            validateIssuer(claims, jwtProperties);
            validateAudience(claims, jwtProperties);

            AppLog.info(log, "security.jwt.parse.succeeded",
                    "source", source,
                    "subject", claims.getSubject(),
                    "issuer", claims.getIssuer());
            return claims;
        } catch (JwtAuthenticationException exception) {
            AppLog.warn(log, "security.jwt.parse.rejected",
                    "source", source,
                    "reason", exception.getMessage());
            throw exception;
        } catch (JwtException exception) {
            AppLog.warn(log, "security.jwt.parse.failed",
                    "source", source,
                    "reason", exception.getClass().getSimpleName(),
                    "message", exception.getMessage());
            throw new JwtAuthenticationException("JWT parsing failed", exception);
        } catch (Exception exception) {
            AppLog.error(log, exception, "security.jwt.parse.failed-unexpected",
                    "source", source,
                    "reason", exception.getClass().getSimpleName());
            throw new JwtAuthenticationException("Unexpected JWT parsing failure", exception);
        }
    }

    private JwtParserBuilder buildParser(JwtProperties jwtProperties) throws Exception {
        JwtParserBuilder builder = Jwts.parser()
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds());
        String algorithm = normalizeAlgorithm(jwtProperties.getAlgorithm());
        if (algorithm.startsWith("HS")) {
            if (!StringUtils.hasText(jwtProperties.getSecret())) {
                AppLog.warn(log, "security.jwt.key.missing-secret",
                        "algorithm", algorithm);
                throw new JwtAuthenticationException("JWT secret is not configured");
            }
            builder.verifyWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)));
            return builder;
        }
        if (algorithm.startsWith("RS")) {
            builder.verifyWith(resolvePublicKey(jwtProperties));
            return builder;
        }
        throw new JwtAuthenticationException("Unsupported JWT algorithm: " + algorithm);
    }

    private void validateIssuer(Claims claims, JwtProperties jwtProperties) {
        if (!StringUtils.hasText(jwtProperties.getIssuer())) {
            return;
        }
        if (!Objects.equals(jwtProperties.getIssuer(), claims.getIssuer())) {
            AppLog.warn(log, "security.jwt.claim.issuer-mismatch",
                    "expected", jwtProperties.getIssuer(),
                    "actual", claims.getIssuer());
            throw new JwtAuthenticationException("JWT issuer mismatch");
        }
    }

    private void validateAudience(Claims claims, JwtProperties jwtProperties) {
        if (!StringUtils.hasText(jwtProperties.getAudience())) {
            return;
        }
        Object audienceClaim = claims.get("aud");
        if (audienceClaim == null) {
            AppLog.warn(log, "security.jwt.claim.audience-missing",
                    "expected", jwtProperties.getAudience());
            throw new JwtAuthenticationException("JWT audience is missing");
        }
        if (audienceClaim instanceof String) {
            if (!jwtProperties.getAudience().equals(audienceClaim)) {
                AppLog.warn(log, "security.jwt.claim.audience-mismatch",
                        "expected", jwtProperties.getAudience(),
                        "actual", audienceClaim);
                throw new JwtAuthenticationException("JWT audience mismatch");
            }
            return;
        }
        if (audienceClaim instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) audienceClaim;
            if (!collection.contains(jwtProperties.getAudience())) {
                AppLog.warn(log, "security.jwt.claim.audience-mismatch",
                        "expected", jwtProperties.getAudience(),
                        "actual", collection);
                throw new JwtAuthenticationException("JWT audience mismatch");
            }
            return;
        }
        AppLog.warn(log, "security.jwt.claim.audience-unsupported",
                "type", audienceClaim.getClass().getName());
        throw new JwtAuthenticationException("Unsupported JWT audience claim type");
    }

    private PublicKey resolvePublicKey(JwtProperties jwtProperties) throws Exception {
        if (!StringUtils.hasText(jwtProperties.getPublicKey())) {
            AppLog.warn(log, "security.jwt.key.missing-public-key",
                    "algorithm", jwtProperties.getAlgorithm());
            throw new JwtAuthenticationException("JWT public key is not configured");
        }
        String normalized = jwtProperties.getPublicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private String normalizeAlgorithm(String algorithm) {
        return algorithm == null ? "" : algorithm.trim().toUpperCase();
    }
}
