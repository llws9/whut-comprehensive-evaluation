package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleDetail;

import java.util.Optional;

public interface RoleAdminCommandRepository {

    Optional<IamRoleDetail> findById(Long roleId);

    Optional<IamRoleDetail> findByRoleCode(String roleCode);

    IamRoleDetail create(String roleCode, String roleName, String roleScope, String status);

    boolean updateWithSnapshot(Long roleId,
                               String roleName,
                               String roleScope,
                               String status,
                               String snapshotRoleName,
                               String snapshotRoleScope,
                               String snapshotStatus);
}
