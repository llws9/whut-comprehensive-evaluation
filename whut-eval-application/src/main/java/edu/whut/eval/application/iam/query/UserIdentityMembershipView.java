package edu.whut.eval.application.iam.query;

public record UserIdentityMembershipView(
        Long id,
        Long userId,
        Long orgUnitId,
        String membershipType,
        String status
) {
}
