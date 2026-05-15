package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefreshTokenClaimsMapper {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenClaimsMapper.class);

    private final SecurityProperties securityProperties;

    public RefreshTokenClaimsMapper(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public RefreshTokenSubject map(Claims claims) {
        JwtProperties jwt = securityProperties.getJwt();
        String tokenType = readRequiredStringClaim(claims, jwt.getTokenTypeClaim());
        if (!jwt.getRefreshTokenType().equals(tokenType)) {
            AppLog.warn(log, "security.jwt.refresh.claim.token-type-mismatch",
                    "expected", jwt.getRefreshTokenType(),
                    "actual", tokenType,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("JWT token type is not refresh");
        }

        Long userId = readRequiredLongClaim(claims, jwt.getUserIdClaim());
        String userNo = readRequiredStringClaim(claims, jwt.getUserNoClaim());
        String identity = readRequiredStringClaim(claims, jwt.getIdentityClaim());

        AppLog.info(log, "security.jwt.refresh.claims.mapped",
                "subject", claims.getSubject(),
                "userId", userId,
                "userNo", userNo,
                "identity", identity);
        return new RefreshTokenSubject(userId, userNo, identity);
    }

    private Long readRequiredLongClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            AppLog.warn(log, "security.jwt.refresh.claim.missing",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("Required refresh token claim is missing: " + claimName);
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException exception) {
                AppLog.warn(log, "security.jwt.refresh.claim.invalid-number",
                        "claim", claimName,
                        "value", value);
                throw new JwtAuthenticationException("Refresh token claim is not a valid number: " + claimName, exception);
            }
        }
        throw new JwtAuthenticationException("Refresh token claim type is invalid: " + claimName);
    }

    private String readRequiredStringClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            AppLog.warn(log, "security.jwt.refresh.claim.missing",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("Required refresh token claim is missing: " + claimName);
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            AppLog.warn(log, "security.jwt.refresh.claim.blank",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("Refresh token claim is blank: " + claimName);
        }
        return text;
    }
}
