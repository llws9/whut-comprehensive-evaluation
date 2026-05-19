package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MybatisPlusRoleAssignmentAdminRepository implements RoleAssignmentAdminRepository {

    private final IamUserRoleAssignmentMapper assignmentMapper;
    private final IamRoleMapper roleMapper;
    private final OrgUnitMapper orgUnitMapper;

    public MybatisPlusRoleAssignmentAdminRepository(IamUserRoleAssignmentMapper assignmentMapper,
                                                    IamRoleMapper roleMapper,
                                                    OrgUnitMapper orgUnitMapper) {
        this.assignmentMapper = assignmentMapper;
        this.roleMapper = roleMapper;
        this.orgUnitMapper = orgUnitMapper;
    }

    @Override
    public boolean existsActiveAssignment(Long userId, String roleCode, Long orgUnitId, Long excludeAssignmentId) {
        IamRoleDO role = roleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        if (role == null) {
            return false;
        }
        QueryWrapper<IamUserRoleAssignmentDO> wrapper = new QueryWrapper<IamUserRoleAssignmentDO>()
                .eq("user_id", userId)
                .eq("role_id", role.getId())
                .eq("org_unit_id", orgUnitId)
                .eq("status", "ACTIVE")
                .le("effective_from", LocalDateTime.now())
                .and(w -> w.isNull("effective_to")
                        .or()
                        .gt("effective_to", LocalDateTime.now()));
        if (excludeAssignmentId != null) {
            wrapper.ne("id", excludeAssignmentId);
        }
        return assignmentMapper.selectCount(wrapper) > 0;
    }

    @Override
    public IamRoleAssignmentDetail create(Long userId,
                                          String roleCode,
                                          String roleName,
                                          Long orgUnitId,
                                          String orgUnitName,
                                          String effectiveFrom,
                                          String effectiveTo,
                                          String sourceType,
                                          Long assignedBy,
                                          String status) {
        IamRoleDO role = roleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        IamUserRoleAssignmentDO assignmentDO = new IamUserRoleAssignmentDO();
        assignmentDO.setUserId(userId);
        assignmentDO.setRoleId(role == null ? null : role.getId());
        assignmentDO.setOrgUnitId(orgUnitId);
        assignmentDO.setSourceType(sourceType);
        assignmentDO.setEffectiveFrom(parseTime(effectiveFrom));
        assignmentDO.setEffectiveTo(parseTime(effectiveTo));
        assignmentDO.setStatus(status);
        assignmentDO.setAssignedBy(assignedBy);
        assignmentDO.setCreatedAt(LocalDateTime.now());
        assignmentMapper.insert(assignmentDO);
        return new IamRoleAssignmentDetail(
                assignmentDO.getId(),
                userId,
                roleCode,
                roleName,
                orgUnitId,
                orgUnitName,
                status,
                effectiveFrom,
                effectiveTo,
                sourceType,
                null
        );
    }

    @Override
    public Optional<IamRoleAssignmentDetail> findDetailById(Long assignmentId) {
        IamUserRoleAssignmentDO assignmentDO = assignmentMapper.selectById(assignmentId);
        return Optional.ofNullable(assignmentDO).map(this::toDetail);
    }

    @Override
    public IamRoleAssignmentDetail update(Long assignmentId,
                                          Long userId,
                                          String roleCode,
                                          String roleName,
                                          Long orgUnitId,
                                          String orgUnitName,
                                          String status,
                                          String effectiveFrom,
                                          String effectiveTo,
                                          String sourceType) {
        IamUserRoleAssignmentDO assignmentDO = assignmentMapper.selectById(assignmentId);
        if (assignmentDO == null) {
            return new IamRoleAssignmentDetail(
                    assignmentId,
                    userId,
                    roleCode,
                    roleName,
                    orgUnitId,
                    orgUnitName,
                    status,
                    effectiveFrom,
                    effectiveTo,
                    sourceType,
                    LocalDateTime.now().toString()
            );
        }
        assignmentDO.setOrgUnitId(orgUnitId);
        assignmentDO.setStatus(status);
        assignmentDO.setEffectiveFrom(parseTime(effectiveFrom));
        assignmentDO.setEffectiveTo(parseTime(effectiveTo));
        assignmentMapper.updateById(assignmentDO);
        return new IamRoleAssignmentDetail(
                assignmentId,
                userId,
                roleCode,
                roleName,
                orgUnitId,
                orgUnitName,
                status,
                effectiveFrom,
                effectiveTo,
                sourceType,
                LocalDateTime.now().toString()
        );
    }

    private IamRoleAssignmentDetail toDetail(IamUserRoleAssignmentDO assignmentDO) {
        IamRoleDO roleDO = assignmentDO.getRoleId() == null ? null : roleMapper.selectById(assignmentDO.getRoleId());
        OrgUnitDO orgUnitDO = assignmentDO.getOrgUnitId() == null ? null : orgUnitMapper.selectById(assignmentDO.getOrgUnitId());
        return new IamRoleAssignmentDetail(
                assignmentDO.getId(),
                assignmentDO.getUserId(),
                roleDO == null ? null : roleDO.getRoleCode(),
                roleDO == null ? null : roleDO.getRoleName(),
                assignmentDO.getOrgUnitId(),
                orgUnitDO == null ? null : orgUnitDO.getUnitName(),
                assignmentDO.getStatus(),
                formatTime(assignmentDO.getEffectiveFrom()),
                formatTime(assignmentDO.getEffectiveTo()),
                assignmentDO.getSourceType(),
                null
        );
    }

    private LocalDateTime parseTime(String time) {
        return time == null || time.isBlank() ? null : LocalDateTime.parse(time);
    }

    private String formatTime(LocalDateTime time) {
        return Objects.toString(time, null);
    }
}
