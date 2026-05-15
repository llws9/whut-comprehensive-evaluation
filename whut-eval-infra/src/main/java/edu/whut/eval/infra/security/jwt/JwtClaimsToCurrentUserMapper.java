package edu.whut.eval.infra.security.jwt;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.context.CurrentUser;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtClaimsToCurrentUserMapper {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimsToCurrentUserMapper.class);

    private final SecurityProperties securityProperties;

    public JwtClaimsToCurrentUserMapper(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public CurrentUser map(Claims claims) {
        JwtProperties jwtProperties = securityProperties.getJwt();
        Long userId = readRequiredLongClaim(claims, jwtProperties.getUserIdClaim());
        String userNo = readRequiredStringClaim(claims, jwtProperties.getUserNoClaim());
        String userName = readRequiredStringClaim(claims, jwtProperties.getUserNameClaim());
        String identity = readRequiredStringClaim(claims, jwtProperties.getIdentityClaim());
        Set<String> roles = readStringSetClaim(claims, jwtProperties.getRolesClaim(), true);
        Set<String> authorities = readAuthorities(claims, jwtProperties.getAuthoritiesClaim());

        AppLog.info(log, "security.jwt.claims.mapped",
                "subject", claims.getSubject(),
                "userId", userId,
                "userNo", userNo,
                "identity", identity,
                "roleCount", roles.size(),
                "authorityCount", authorities.size());
        return new CurrentUser(userId, userNo, userName, identity, roles, authorities, java.util.List.of());
    }

    private Long readRequiredLongClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            AppLog.warn(log, "security.jwt.claim.missing",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("Required JWT claim is missing: " + claimName);
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException exception) {
                AppLog.warn(log, "security.jwt.claim.invalid-number",
                        "claim", claimName,
                        "value", value);
                throw new JwtAuthenticationException("JWT claim is not a valid number: " + claimName, exception);
            }
        }
        AppLog.warn(log, "security.jwt.claim.invalid-type",
                "claim", claimName,
                "type", value.getClass().getName());
        throw new JwtAuthenticationException("JWT claim type is invalid: " + claimName);
    }

    private String readRequiredStringClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            AppLog.warn(log, "security.jwt.claim.missing",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("Required JWT claim is missing: " + claimName);
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            AppLog.warn(log, "security.jwt.claim.blank",
                    "claim", claimName,
                    "subject", claims.getSubject());
            throw new JwtAuthenticationException("JWT claim is blank: " + claimName);
        }
        return text;
    }

    private Set<String> readAuthorities(Claims claims, String claimName) {
        return readStringSetClaim(claims, claimName, false);
    }

    private Set<String> readStringSetClaim(Claims claims, String claimName, boolean required) {
        Object value = claims.get(claimName);
        if (value == null) {
            if (required) {
                AppLog.warn(log, "security.jwt.claim.missing",
                        "claim", claimName,
                        "subject", claims.getSubject());
                throw new JwtAuthenticationException("Required JWT claim is missing: " + claimName);
            }
            AppLog.info(log, "security.jwt.claim.collection-missing",
                    "claim", claimName,
                    "subject", claims.getSubject());
            return Set.of();
        }

        Set<String> values = new LinkedHashSet<>();
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            for (Object item : collection) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            }
            AppLog.info(log, "security.jwt.claim.collection-parsed",
                    "claim", claimName,
                    "format", "collection",
                    "count", values.size());
            return values;
        }

        if (value instanceof String) {
            String text = ((String) value).trim();
            if (!StringUtils.hasText(text)) {
                if (required) {
                    AppLog.warn(log, "security.jwt.claim.blank",
                            "claim", claimName,
                            "subject", claims.getSubject());
                    throw new JwtAuthenticationException("JWT claim is blank: " + claimName);
                }
                return Set.of();
            }
            String[] parts = text.split(",");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    values.add(part.trim());
                }
            }
            AppLog.info(log, "security.jwt.claim.collection-parsed",
                    "claim", claimName,
                    "format", "comma-separated",
                    "count", values.size());
            return values;
        }

        AppLog.warn(log, "security.jwt.claim.collection-invalid-type",
                "claim", claimName,
                "type", value.getClass().getName());
        throw new JwtAuthenticationException("JWT claim type is invalid: " + claimName);
    }
}
