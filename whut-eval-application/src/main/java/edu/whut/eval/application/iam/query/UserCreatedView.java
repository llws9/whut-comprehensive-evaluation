package edu.whut.eval.application.iam.query;

public record UserCreatedView(
        Long userId,
        String userNo,
        String userName,
        String status
) {
}