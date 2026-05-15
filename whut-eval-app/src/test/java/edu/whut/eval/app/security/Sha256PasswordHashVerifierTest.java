package edu.whut.eval.app.security;

import edu.whut.eval.infra.security.password.Sha256PasswordHashVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256PasswordHashVerifierTest {

    private final Sha256PasswordHashVerifier verifier = new Sha256PasswordHashVerifier();

    @Test
    void shouldMatchSha256Hash() {
        assertThat(verifier.matches(
                "ChangeMe123!",
                "9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b"
        )).isTrue();
    }

    @Test
    void shouldReturnFalseForInvalidHash() {
        assertThat(verifier.matches("ChangeMe123!", "not-a-valid-hash")).isFalse();
    }
}
