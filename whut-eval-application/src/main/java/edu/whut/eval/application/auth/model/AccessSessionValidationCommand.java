package edu.whut.eval.application.auth.model;

public record AccessSessionValidationCommand(
        Long userId,
        String sessionNo,
        String accessTokenId
) {
}
