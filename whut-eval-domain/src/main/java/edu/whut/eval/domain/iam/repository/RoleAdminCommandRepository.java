package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRole;

import java.util.List;
import java.util.Optional;

public interface RoleAdminCommandRepository {

    IamRole create(String roleCode, String roleName, String roleScope, String status);

    Optional<IamRole> findById(Long roleId);

    boolean update(IamRole existingRole, String roleName, String roleScope, String status);

    void replacePermissions(Long roleId, List<String> permissionCodes);
}
