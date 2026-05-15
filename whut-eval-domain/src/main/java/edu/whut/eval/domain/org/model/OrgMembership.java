package edu.whut.eval.domain.org.model;

public record OrgMembership(
        Long id,
        Long userId,
        Long orgUnitId,
        String membershipType,
        String status
) {
}
