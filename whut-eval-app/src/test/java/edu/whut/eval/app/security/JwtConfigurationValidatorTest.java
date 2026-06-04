package edu.whut.eval.app.security;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationValidatorTest {

    @Test
    void shouldPassWhenHsConfigurationIsValid() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setSecret("test-only-strong-jwt-secret-1234567890");

        JwtConfigurationValidator validator = new JwtConfigurationValidator(properties);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenHsSecretUsesBuiltInPlaceholder() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setSecret("local-dev-jwt-secret-change-me-1234567890");

        JwtConfigurationValidator validator = new JwtConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("must not use placeholder secret");
    }

    @Test
    void shouldFailWhenHsSecretUsesRequiredNotSetPlaceholder() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setSecret("REQUIRED_NOT_SET");

        JwtConfigurationValidator validator = new JwtConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("must not use placeholder secret");
    }

    @Test
    void shouldUseExplicitRequiredNotSetPlaceholderInDefaultApplicationConfig() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("secret: ${WHUT_EVAL_JWT_SECRET:REQUIRED_NOT_SET}");
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
