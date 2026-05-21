package edu.whut.eval.infra.security.password;

import edu.whut.eval.application.auth.service.PasswordHasher;
import edu.whut.eval.application.auth.service.PasswordHashVerifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256PasswordHashVerifier implements PasswordHashVerifier, PasswordHasher {

    @Override
    public boolean matches(String rawPassword, String storedPasswordHash) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        if (storedPasswordHash == null || storedPasswordHash.isBlank()) {
            return false;
        }
        try {
            byte[] expected = normalizeHash(storedPasswordHash);
            byte[] actual = normalizeHash(hash(rawPassword));
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private byte[] normalizeHash(String hash) {
        return HexFormat.of().parseHex(hash.trim().toLowerCase());
    }
}
