package edu.whut.eval.domain.org.repository;

import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;

import java.util.List;
import java.util.Optional;

public interface OrgQueryRepository {

    Optional<OrgUnit> findByCode(String unitCode);

    List<OrgUnit> findChildren(Long parentId);

    List<OrgMembership> findMembershipsByUserId(Long userId);
}
