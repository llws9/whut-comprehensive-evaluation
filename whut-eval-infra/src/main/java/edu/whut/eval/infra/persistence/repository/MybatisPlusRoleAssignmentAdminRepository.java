package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentPageItem;
import edu.whut.eval.domain.iam.model.RoleAssignmentCurrentStatusResolver;
import edu.whut.eval.domain.iam.query.RoleAssignmentPageQuery;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusRoleAssignmentAdminRepository implements RoleAssignmentAdminRepository {

    private final IamUserRoleAssignmentMapper assignmentMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserMapper userMapper;
    private final OrgUnitMapper orgUnitMapper;

    public MybatisPlusRoleAssignmentAdminRepository(IamUserRoleAssignmentMapper assignmentMapper,
                                                    IamRoleMapper roleMapper,
                                                    IamUserMapper userMapper,
                                                    OrgUnitMapper orgUnitMapper) {
        this.assignmentMapper = assignmentMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
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
    public PageResult<IamRoleAssignmentPageItem> pageAssignments(RoleAssignmentPageQuery query) {
        Long roleId = resolveRoleId(query.roleCode());
        if (query.roleCode() != null && !query.roleCode().isBlank() && roleId == null) {
            return new PageResult<>(0, List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        Page<IamUserRoleAssignmentDO> page = Page.of(query.pageNo(), query.pageSize());
        QueryWrapper<IamUserRoleAssignmentDO> wrapper = new QueryWrapper<IamUserRoleAssignmentDO>()
                .eq(query.userId() != null, "user_id", query.userId())
                .eq(roleId != null, "role_id", roleId)
                .eq(query.orgUnitId() != null, "org_unit_id", query.orgUnitId())
                .orderByAsc("id");
        appendStatusFilter(wrapper, query.status(), now);

        Page<IamUserRoleAssignmentDO> result = assignmentMapper.selectPage(page, wrapper);
        List<IamUserRoleAssignmentDO> assignments = result.getRecords();
        Map<Long, IamUserDO> users = mapById(userMapper.selectBatchIds(idsOf(assignments, IamUserRoleAssignmentDO::getUserId)), IamUserDO::getId);
        Map<Long, IamRoleDO> roles = mapById(roleMapper.selectBatchIds(idsOf(assignments, IamUserRoleAssignmentDO::getRoleId)), IamRoleDO::getId);
        Map<Long, OrgUnitDO> orgUnits = mapById(orgUnitMapper.selectBatchIds(idsOf(assignments, IamUserRoleAssignmentDO::getOrgUnitId)), OrgUnitDO::getId);

        return new PageResult<>(
                result.getTotal(),
                assignments.stream()
                        .map(item -> toPageItem(item, users.get(item.getUserId()), roles.get(item.getRoleId()), orgUnits.get(item.getOrgUnitId()), now))
                        .toList()
        );
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
            throw new ConflictException("角色分配已变更，请刷新后重试");
        }
        LocalDateTime nextEffectiveFrom = parseTime(effectiveFrom);
        LocalDateTime nextEffectiveTo = parseTime(effectiveTo);
        IamUserRoleAssignmentDO updatedDO = new IamUserRoleAssignmentDO();
        updatedDO.setOrgUnitId(orgUnitId);
        updatedDO.setStatus(status);
        updatedDO.setEffectiveFrom(nextEffectiveFrom);
        updatedDO.setEffectiveTo(nextEffectiveTo);
        int updated = assignmentMapper.update(updatedDO, buildCurrentRowGuard(assignmentDO));
        if (updated == 0) {
            throw new ConflictException("角色分配已变更，请刷新后重试");
        }
        assignmentDO.setOrgUnitId(orgUnitId);
        assignmentDO.setStatus(status);
        assignmentDO.setEffectiveFrom(nextEffectiveFrom);
        assignmentDO.setEffectiveTo(nextEffectiveTo);
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

    @Override
    public IamRoleAssignmentDetail revoke(Long assignmentId) {
        IamUserRoleAssignmentDO assignmentDO = assignmentMapper.selectById(assignmentId);
        if (assignmentDO == null) {
            throw new ConflictException("角色分配已变更，请刷新后重试");
        }
        IamUserRoleAssignmentDO revokedDO = new IamUserRoleAssignmentDO();
        revokedDO.setStatus("INACTIVE");
        int updated = assignmentMapper.update(revokedDO, buildCurrentRowGuard(assignmentDO));
        if (updated == 0) {
            throw new ConflictException("角色分配已变更，请刷新后重试");
        }
        assignmentDO.setStatus("INACTIVE");
        return toDetail(assignmentDO, LocalDateTime.now().toString());
    }

    private IamRoleAssignmentDetail toDetail(IamUserRoleAssignmentDO assignmentDO) {
        IamRoleDO roleDO = assignmentDO.getRoleId() == null ? null : roleMapper.selectById(assignmentDO.getRoleId());
        OrgUnitDO orgUnitDO = assignmentDO.getOrgUnitId() == null ? null : orgUnitMapper.selectById(assignmentDO.getOrgUnitId());
        return toDetail(assignmentDO, null, roleDO, orgUnitDO);
    }

    private IamRoleAssignmentDetail toDetail(IamUserRoleAssignmentDO assignmentDO, String updatedAt) {
        IamRoleDO roleDO = assignmentDO.getRoleId() == null ? null : roleMapper.selectById(assignmentDO.getRoleId());
        OrgUnitDO orgUnitDO = assignmentDO.getOrgUnitId() == null ? null : orgUnitMapper.selectById(assignmentDO.getOrgUnitId());
        return toDetail(assignmentDO, updatedAt, roleDO, orgUnitDO);
    }

    private IamRoleAssignmentDetail toDetail(IamUserRoleAssignmentDO assignmentDO,
                                             String updatedAt,
                                             IamRoleDO roleDO,
                                             OrgUnitDO orgUnitDO) {
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
                updatedAt
        );
    }

    private IamRoleAssignmentPageItem toPageItem(IamUserRoleAssignmentDO assignmentDO,
                                                 IamUserDO userDO,
                                                 IamRoleDO roleDO,
                                                 OrgUnitDO orgUnitDO,
                                                 LocalDateTime now) {
        return new IamRoleAssignmentPageItem(
                assignmentDO.getId(),
                assignmentDO.getUserId(),
                userDO == null ? null : userDO.getUserNo(),
                userDO == null ? null : userDO.getUserName(),
                roleDO == null ? null : roleDO.getRoleCode(),
                roleDO == null ? null : roleDO.getRoleName(),
                assignmentDO.getOrgUnitId(),
                orgUnitDO == null ? null : orgUnitDO.getUnitName(),
                resolveDisplayStatus(assignmentDO, now),
                formatTime(assignmentDO.getEffectiveFrom()),
                formatTime(assignmentDO.getEffectiveTo())
        );
    }

    private void appendStatusFilter(QueryWrapper<IamUserRoleAssignmentDO> wrapper, String status, LocalDateTime now) {
        if (status == null || status.isBlank()) {
            return;
        }
        if ("ACTIVE".equals(status)) {
            wrapper.eq("status", "ACTIVE")
                    .le("effective_from", now)
                    .and(w -> w.isNull("effective_to").or().gt("effective_to", now));
            return;
        }
        if ("INACTIVE".equals(status)) {
            wrapper.and(w -> w.eq("status", "INACTIVE")
                    .or(future -> future.eq("status", "ACTIVE").gt("effective_from", now)));
            return;
        }
        wrapper.and(w -> w.eq("status", "EXPIRED")
                .or(expired -> expired.eq("status", "ACTIVE").isNotNull("effective_to").le("effective_to", now)));
    }

    private String resolveDisplayStatus(IamUserRoleAssignmentDO assignmentDO, LocalDateTime now) {
        return RoleAssignmentCurrentStatusResolver.resolve(
                assignmentDO.getStatus(),
                assignmentDO.getEffectiveFrom(),
                assignmentDO.getEffectiveTo(),
                now
        );
    }

    private Long resolveRoleId(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        IamRoleDO role = roleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        return role == null ? null : role.getId();
    }

    private LambdaUpdateWrapper<IamUserRoleAssignmentDO> buildCurrentRowGuard(IamUserRoleAssignmentDO assignmentDO) {
        LambdaUpdateWrapper<IamUserRoleAssignmentDO> wrapper = new LambdaUpdateWrapper<IamUserRoleAssignmentDO>()
                .eq(IamUserRoleAssignmentDO::getId, assignmentDO.getId())
                .eq(IamUserRoleAssignmentDO::getUserId, assignmentDO.getUserId())
                .eq(IamUserRoleAssignmentDO::getRoleId, assignmentDO.getRoleId())
                .eq(IamUserRoleAssignmentDO::getOrgUnitId, assignmentDO.getOrgUnitId())
                .eq(IamUserRoleAssignmentDO::getStatus, assignmentDO.getStatus())
                .eq(IamUserRoleAssignmentDO::getSourceType, assignmentDO.getSourceType());
        eqNullable(wrapper, IamUserRoleAssignmentDO::getEffectiveFrom, assignmentDO.getEffectiveFrom());
        eqNullable(wrapper, IamUserRoleAssignmentDO::getEffectiveTo, assignmentDO.getEffectiveTo());
        appendCurrentWindowGuard(wrapper, assignmentDO);
        return wrapper;
    }

    private <T> Map<Long, T> mapById(List<T> items, Function<T, Long> idGetter) {
        return items.stream().collect(Collectors.toMap(idGetter, Function.identity()));
    }

    private <T> List<Long> idsOf(List<T> items, Function<T, Long> idGetter) {
        return items.stream()
                .map(idGetter)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private LocalDateTime parseTime(String time) {
        return time == null || time.isBlank() ? null : LocalDateTime.parse(time);
    }

    private <T> void eqNullable(LambdaUpdateWrapper<IamUserRoleAssignmentDO> wrapper,
                                com.baomidou.mybatisplus.core.toolkit.support.SFunction<IamUserRoleAssignmentDO, T> column,
                                T value) {
        if (value == null) {
            wrapper.isNull(column);
            return;
        }
        wrapper.eq(column, value);
    }

    private void appendCurrentWindowGuard(LambdaUpdateWrapper<IamUserRoleAssignmentDO> wrapper,
                                          IamUserRoleAssignmentDO assignmentDO) {
        if (!"ACTIVE".equals(assignmentDO.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        wrapper.le(IamUserRoleAssignmentDO::getEffectiveFrom, now)
                .and(w -> w.isNull(IamUserRoleAssignmentDO::getEffectiveTo)
                        .or()
                        .gt(IamUserRoleAssignmentDO::getEffectiveTo, now));
    }

    private String formatTime(LocalDateTime time) {
        return Objects.toString(time, null);
    }
}
