package edu.whut.eval.application.iam.query;

public record UserMembershipAdminView(
        Long membershipId,
        Long orgUnitId,
        String orgUnitName,
        String orgUnitType,
        boolean isPrimary,
        String status
) {
}
