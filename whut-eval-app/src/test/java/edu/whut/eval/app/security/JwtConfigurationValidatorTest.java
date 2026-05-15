package edu.whut.eval.app.security;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationValidatorTest {

    @Test
    void shouldPassWhenHsConfigurationIsValid() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setSecret("local-dev-jwt-secret-change-me-1234567890");

        JwtConfigurationValidator validator = new JwtConfigurationValidator(properties);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenHsSecretIsBlank() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setSecret("");

        JwtConfigurationValidator validator = new JwtConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("infra.security.jwt.secret");
    }
}
