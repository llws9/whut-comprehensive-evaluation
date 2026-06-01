package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;

public interface IamRoleCommandRepository {

    IamRoleDefinition createRole(String roleCode, String roleName, String roleScope, String status);
}
