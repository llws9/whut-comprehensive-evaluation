package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;

import java.util.Optional;

public interface IamRoleQueryRepository {

    Optional<IamRoleDefinition> findByRoleCode(String roleCode);
}
