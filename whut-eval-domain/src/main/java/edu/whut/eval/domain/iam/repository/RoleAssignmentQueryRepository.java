package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleAssignment;

import java.util.List;

public interface RoleAssignmentQueryRepository {

    List<IamRoleAssignment> findActiveAssignmentsByUserId(Long userId);

    boolean existsActiveAssignment(Long userId, String roleCode, Long orgUnitId);
}
