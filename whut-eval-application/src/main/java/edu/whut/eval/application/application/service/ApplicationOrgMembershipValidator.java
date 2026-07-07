package edu.whut.eval.application.application.service;

public interface ApplicationOrgMembershipValidator {

    boolean isActiveMember(Long userId, Long orgUnitId);
}
