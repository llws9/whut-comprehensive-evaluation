package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.context.CurrentUser;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenIssuer.class);

    private final SecurityProperties securityProperties;

    public JwtTokenIssuer(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public JwtTokenPair issueTokenPair(CurrentUser currentUser) {
        Instant issuedAt = Instant.now();
        Instant accessExpiresAt = issuedAt.plusSeconds(securityProperties.getJwt().getAccessTokenTtlSeconds());
        Instant refreshExpiresAt = issuedAt.plusSeconds(securityProperties.getJwt().getRefreshTokenTtlSeconds());

        String accessToken = issueAccessToken(currentUser, issuedAt, accessExpiresAt);
        String refreshToken = issueRefreshToken(currentUser, issuedAt, refreshExpiresAt);

        AppLog.info(log, "security.jwt.issue.token-pair",
                "userId", currentUser.getUserId(),
                "userNo", currentUser.getUserNo(),
                "identity", currentUser.getIdentity(),
                "roleCount", currentUser.getRoles().size(),
                "authorityCount", currentUser.getAuthorities().size(),
                "accessExpiresAt", accessExpiresAt,
                "refreshExpiresAt", refreshExpiresAt);
        return new JwtTokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    public String issueAccessToken(CurrentUser currentUser) {
        Instant issuedAt = Instant.now();
        return issueAccessToken(currentUser,
                issuedAt,
                issuedAt.plusSeconds(securityProperties.getJwt().getAccessTokenTtlSeconds()));
    }

    public String issueRefreshToken(CurrentUser currentUser) {
        Instant issuedAt = Instant.now();
        return issueRefreshToken(currentUser,
                issuedAt,
                issuedAt.plusSeconds(securityProperties.getJwt().getRefreshTokenTtlSeconds()));
    }

    String issueAccessToken(CurrentUser currentUser, Instant issuedAt, Instant expiresAt) {
        JwtProperties jwt = securityProperties.getJwt();
        try {
            JwtBuilder builder = Jwts.builder()
                    .subject(String.valueOf(currentUser.getUserId()))
                    .issuer(jwt.getIssuer())
                    .audience().add(jwt.getAudience()).and()
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt))
                    .claim(jwt.getTokenTypeClaim(), jwt.getAccessTokenType())
                    .claim(jwt.getUserIdClaim(), currentUser.getUserId())
                    .claim(jwt.getUserNoClaim(), currentUser.getUserNo())
                    .claim(jwt.getUserNameClaim(), currentUser.getUserName())
                    .claim(jwt.getIdentityClaim(), currentUser.getIdentity())
                    .claim(jwt.getRolesClaim(), currentUser.getRoles())
                    .claim(jwt.getAuthoritiesClaim(), currentUser.getAuthorities());
            String token = applySignature(builder, jwt).compact();
            AppLog.info(log, "security.jwt.issue.access.succeeded",
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo(),
                    "identity", currentUser.getIdentity(),
                    "expiresAt", expiresAt);
            return token;
        } catch (Exception exception) {
            AppLog.error(log, exception, "security.jwt.issue.access.failed",
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo());
            throw new JwtAuthenticationException("Failed to issue access token", exception);
        }
    }

    String issueRefreshToken(CurrentUser currentUser, Instant issuedAt, Instant expiresAt) {
        JwtProperties jwt = securityProperties.getJwt();
        try {
            JwtBuilder builder = Jwts.builder()
                    .subject(String.valueOf(currentUser.getUserId()))
                    .issuer(jwt.getIssuer())
                    .audience().add(jwt.getAudience()).and()
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(expiresAt))
                    .claim(jwt.getTokenTypeClaim(), jwt.getRefreshTokenType())
                    .claim(jwt.getUserIdClaim(), currentUser.getUserId())
                    .claim(jwt.getUserNoClaim(), currentUser.getUserNo())
                    .claim(jwt.getIdentityClaim(), currentUser.getIdentity());
            String token = applySignature(builder, jwt).compact();
            AppLog.info(log, "security.jwt.issue.refresh.succeeded",
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo(),
                    "identity", currentUser.getIdentity(),
                    "expiresAt", expiresAt);
            return token;
        } catch (Exception exception) {
            AppLog.error(log, exception, "security.jwt.issue.refresh.failed",
                    "userId", currentUser.getUserId(),
                    "userNo", currentUser.getUserNo());
            throw new JwtAuthenticationException("Failed to issue refresh token", exception);
        }
    }

    private JwtBuilder applySignature(JwtBuilder builder, JwtProperties jwt) throws Exception {
        String algorithm = normalizeAlgorithm(jwt.getAlgorithm());
        if (algorithm.startsWith("HS")) {
            if (!StringUtils.hasText(jwt.getSecret())) {
                throw new JwtAuthenticationException("JWT secret is not configured");
            }
            return applyHmacSignature(builder, algorithm, jwt);
        }
        if (algorithm.startsWith("RS")) {
            return applyRsaSignature(builder, algorithm, jwt);
        }
        throw new JwtAuthenticationException("Unsupported JWT algorithm: " + algorithm);
    }

    private JwtBuilder applyHmacSignature(JwtBuilder builder, String algorithm, JwtProperties jwt) {
        javax.crypto.SecretKey key = Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8));
        switch (algorithm) {
            case "HS256":
                return builder.signWith(key, Jwts.SIG.HS256);
            case "HS384":
                return builder.signWith(key, Jwts.SIG.HS384);
            case "HS512":
                return builder.signWith(key, Jwts.SIG.HS512);
            default:
                throw new JwtAuthenticationException("Unsupported JWT algorithm: " + algorithm);
        }
    }

    private JwtBuilder applyRsaSignature(JwtBuilder builder, String algorithm, JwtProperties jwt) throws Exception {
        PrivateKey key = resolvePrivateKey(jwt);
        switch (algorithm) {
            case "RS256":
                return builder.signWith(key, Jwts.SIG.RS256);
            case "RS384":
                return builder.signWith(key, Jwts.SIG.RS384);
            case "RS512":
                return builder.signWith(key, Jwts.SIG.RS512);
            default:
                throw new JwtAuthenticationException("Unsupported JWT algorithm: " + algorithm);
        }
    }

    private PrivateKey resolvePrivateKey(JwtProperties jwt) throws Exception {
        if (!StringUtils.hasText(jwt.getPrivateKey())) {
            throw new JwtAuthenticationException("JWT private key is not configured");
        }
        String normalized = jwt.getPrivateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private String normalizeAlgorithm(String algorithm) {
        return algorithm == null ? "" : algorithm.trim().toUpperCase();
    }
}
