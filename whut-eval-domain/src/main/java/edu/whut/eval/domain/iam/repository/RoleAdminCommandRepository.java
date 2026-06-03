package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleDetail;

import java.util.Optional;

public interface RoleAdminCommandRepository {

    Optional<IamRoleDetail> findById(Long roleId);

    Optional<IamRoleDetail> findByRoleCode(String roleCode);

    IamRoleDetail create(String roleCode, String roleName, String roleScope, String status);

    void update(Long roleId, String roleName, String roleScope, String status);
}
