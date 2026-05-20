package edu.whut.eval.domain.org.repository;

import edu.whut.eval.domain.org.model.OrgMembership;

import java.util.List;

public interface UserMembershipAdminRepository {

    void lockUserForMembershipReplace(Long userId);

    List<OrgMembership> findActiveMembershipsByUserId(Long userId);

    void replaceMemberships(Long userId, List<OrgMembership> activeMemberships, List<OrgMembership> inactiveMemberships);
}
