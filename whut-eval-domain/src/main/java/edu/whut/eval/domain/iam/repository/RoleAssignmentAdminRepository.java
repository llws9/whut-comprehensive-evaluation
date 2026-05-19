package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentPageItem;
import edu.whut.eval.domain.iam.query.RoleAssignmentPageQuery;
import edu.whut.eval.domain.shared.PageResult;

import java.util.Optional;

public interface RoleAssignmentAdminRepository {

    boolean existsActiveAssignment(Long userId, String roleCode, Long orgUnitId, Long excludeAssignmentId);

    IamRoleAssignmentDetail create(Long userId,
                                   String roleCode,
                                   String roleName,
                                   Long orgUnitId,
                                   String orgUnitName,
                                   String effectiveFrom,
                                   String effectiveTo,
                                   String sourceType,
                                   Long assignedBy,
                                   String status);

    Optional<IamRoleAssignmentDetail> findDetailById(Long assignmentId);

    PageResult<IamRoleAssignmentPageItem> pageAssignments(RoleAssignmentPageQuery query);

    IamRoleAssignmentDetail update(Long assignmentId,
                                   Long userId,
                                   String roleCode,
                                   String roleName,
                                   Long orgUnitId,
                                   String orgUnitName,
                                   String status,
                                   String effectiveFrom,
                                   String effectiveTo,
                                   String sourceType);

    IamRoleAssignmentDetail revoke(Long assignmentId);
}
