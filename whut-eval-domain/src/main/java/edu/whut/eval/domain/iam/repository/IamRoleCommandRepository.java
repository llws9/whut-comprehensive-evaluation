package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;

import java.util.List;

public interface IamRoleCommandRepository {

    IamRoleDefinition createRole(String roleCode, String roleName, String roleScope, String status);

    IamRoleDefinition updateRole(Long roleId, String roleName, String status);

    void replaceRolePermissions(Long roleId, List<String> permissionCodes);
}
