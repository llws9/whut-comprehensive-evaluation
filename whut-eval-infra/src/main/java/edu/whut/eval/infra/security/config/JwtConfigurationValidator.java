package edu.whut.eval.infra.security.config;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.log.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtConfigurationValidator implements InitializingBean {

    private static final String BUILT_IN_HS_PLACEHOLDER = "local-dev-jwt-secret-change-me-1234567890";

    private static final Logger log = LoggerFactory.getLogger(JwtConfigurationValidator.class);

    private final SecurityProperties securityProperties;

    public JwtConfigurationValidator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public void afterPropertiesSet() {
        JwtProperties jwt = securityProperties.getJwt();
        if (!jwt.isEnabled()) {
            AppLog.info(log, "security.jwt.config.validation.skipped",
                    "reason", "disabled");
            return;
        }

        validateNotBlank(jwt.getAlgorithm(), "infra.security.jwt.algorithm");
        validateNotBlank(jwt.getIssuer(), "infra.security.jwt.issuer");
        validateNotBlank(jwt.getAudience(), "infra.security.jwt.audience");
        validatePositive(jwt.getAccessTokenTtlSeconds(), "infra.security.jwt.access-token-ttl-seconds");
        validatePositive(jwt.getRefreshTokenTtlSeconds(), "infra.security.jwt.refresh-token-ttl-seconds");
        validatePositive(jwt.getClockSkewSeconds(), "infra.security.jwt.clock-skew-seconds");
        validateNotBlank(jwt.getUserIdClaim(), "infra.security.jwt.user-id-claim");
        validateNotBlank(jwt.getUserNoClaim(), "infra.security.jwt.user-no-claim");
        validateNotBlank(jwt.getUserNameClaim(), "infra.security.jwt.user-name-claim");
        validateNotBlank(jwt.getIdentityClaim(), "infra.security.jwt.identity-claim");
        validateNotBlank(jwt.getRolesClaim(), "infra.security.jwt.roles-claim");
        validateNotBlank(jwt.getAuthoritiesClaim(), "infra.security.jwt.authorities-claim");
        validateNotBlank(jwt.getTokenTypeClaim(), "infra.security.jwt.token-type-claim");
        validateNotBlank(jwt.getAccessTokenType(), "infra.security.jwt.access-token-type");
        validateNotBlank(jwt.getRefreshTokenType(), "infra.security.jwt.refresh-token-type");

        String algorithm = jwt.getAlgorithm().trim().toUpperCase();
        if (algorithm.startsWith("HS")) {
            validateNotBlank(jwt.getSecret(), "infra.security.jwt.secret");
            validateNotPlaceholder(jwt.getSecret(), "infra.security.jwt.secret");
            validateMinimumSecretLength(jwt.getSecret(), "infra.security.jwt.secret", 32);
        } else if (algorithm.startsWith("RS")) {
            validateNotBlank(jwt.getPublicKey(), "infra.security.jwt.public-key");
        } else {
            throw new ConfigLoadException("Unsupported JWT algorithm: " + jwt.getAlgorithm());
        }

        AppLog.info(log, "security.jwt.config.validation.passed",
                "algorithm", algorithm,
                "issuer", jwt.getIssuer(),
                "audience", jwt.getAudience());
    }

    private void validateNotBlank(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new ConfigLoadException(propertyName + " must not be blank");
        }
    }

    private void validatePositive(long value, String propertyName) {
        if (value <= 0) {
            throw new ConfigLoadException(propertyName + " must be greater than 0");
        }
    }

    private void validateMinimumSecretLength(String value, String propertyName, int minimumLength) {
        if (value.length() < minimumLength) {
            throw new ConfigLoadException(propertyName + " must be at least " + minimumLength + " characters for HS algorithms");
        }
    }

    private void validateNotPlaceholder(String value, String propertyName) {
        if (BUILT_IN_HS_PLACEHOLDER.equals(value)) {
            throw new ConfigLoadException(propertyName + " must not use built-in placeholder secret");
        }
    }
}
