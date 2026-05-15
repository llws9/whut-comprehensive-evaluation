package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusRoleAssignmentQueryRepository implements RoleAssignmentQueryRepository {

    private final IamUserRoleAssignmentMapper assignmentMapper;
    private final IamRoleMapper roleMapper;

    public MybatisPlusRoleAssignmentQueryRepository(IamUserRoleAssignmentMapper assignmentMapper, IamRoleMapper roleMapper) {
        this.assignmentMapper = assignmentMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<IamRoleAssignment> findActiveAssignmentsByUserId(Long userId) {
        List<IamUserRoleAssignmentDO> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<IamUserRoleAssignmentDO>()
                        .eq(IamUserRoleAssignmentDO::getUserId, userId)
                        .eq(IamUserRoleAssignmentDO::getStatus, "ACTIVE")
        );
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = assignments.stream().map(IamUserRoleAssignmentDO::getRoleId).collect(Collectors.toSet());
        Map<Long, IamRoleDO> roleMap = roleIds.isEmpty() ? Collections.emptyMap() : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(IamRoleDO::getId, Function.identity()));
        return assignments.stream()
                .map(assignment -> {
                    IamRoleDO role = roleMap.get(assignment.getRoleId());
                    return new IamRoleAssignment(
                            assignment.getId(),
                            assignment.getRoleId(),
                            role == null ? null : role.getRoleCode(),
                            role == null ? null : role.getRoleName(),
                            assignment.getOrgUnitId(),
                            assignment.getStatus()
                    );
                })
                .toList();
    }

    @Override
    public boolean existsActiveAssignment(Long userId, String roleCode, Long orgUnitId) {
        return findActiveAssignmentsByUserId(userId).stream()
                .anyMatch(assignment -> roleCode.equals(assignment.roleCode()) && Objects.equals(orgUnitId, assignment.orgUnitId()));
    }
}
