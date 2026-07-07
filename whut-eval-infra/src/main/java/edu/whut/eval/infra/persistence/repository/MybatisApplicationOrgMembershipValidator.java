package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.application.service.ApplicationOrgMembershipValidator;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisApplicationOrgMembershipValidator implements ApplicationOrgMembershipValidator {

    private final OrgMembershipMapper orgMembershipMapper;

    public MybatisApplicationOrgMembershipValidator(OrgMembershipMapper orgMembershipMapper) {
        this.orgMembershipMapper = orgMembershipMapper;
    }

    @Override
    public boolean isActiveMember(Long userId, Long orgUnitId) {
        return orgMembershipMapper.countActiveByUserIdAndOrgUnitId(userId, orgUnitId) > 0;
    }
}
