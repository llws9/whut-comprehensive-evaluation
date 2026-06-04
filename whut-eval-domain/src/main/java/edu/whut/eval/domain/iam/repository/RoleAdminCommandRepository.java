package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;

import java.util.List;
import java.util.Optional;

public interface RoleAdminCommandRepository {

    Optional<IamRoleAdminPageItem> findByRoleCode(String roleCode);

    boolean existsById(Long roleId);

    IamRoleAdminPageItem createRole(String roleCode, String roleName, String roleScope);

    boolean updateRoleIfSnapshotMatches(Long roleId,
                                        String roleName,
                                        String roleScope,
                                        String status,
                                        String expectedRoleName,
                                        String expectedRoleScope,
                                        String expectedStatus);

    void replacePermissions(Long roleId, List<String> permissionCodes);
}
