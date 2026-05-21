package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.domain.iam.model.IamUserAdminPageItem;
import edu.whut.eval.domain.iam.query.UserAdminPageQuery;
import edu.whut.eval.domain.iam.repository.UserAdminQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.entity.IamUserRoleAssignmentDO;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusUserAdminQueryRepository implements UserAdminQueryRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IamUserMapper iamUserMapper;
    private final OrgMembershipMapper orgMembershipMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final IamUserRoleAssignmentMapper iamUserRoleAssignmentMapper;
    private final IamRoleMapper iamRoleMapper;

    public MybatisPlusUserAdminQueryRepository(IamUserMapper iamUserMapper,
                                               OrgMembershipMapper orgMembershipMapper,
                                               OrgUnitMapper orgUnitMapper,
                                               IamUserRoleAssignmentMapper iamUserRoleAssignmentMapper,
                                               IamRoleMapper iamRoleMapper) {
        this.iamUserMapper = iamUserMapper;
        this.orgMembershipMapper = orgMembershipMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.iamUserRoleAssignmentMapper = iamUserRoleAssignmentMapper;
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public PageResult<IamUserAdminPageItem> pageUsers(UserAdminPageQuery query) {
        Page<IamUserDO> page = Page.of(query.pageNo(), query.pageSize());
        LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<IamUserDO>()
                .and(query.keyword() != null, q -> q.like(IamUserDO::getUserNo, query.keyword())
                        .or()
                        .like(IamUserDO::getUserName, query.keyword()))
                .eq(query.status() != null, IamUserDO::getStatus, query.status())
                .orderByAsc(IamUserDO::getId);

        if (query.orgUnitId() != null) {
            List<Long> userIds = orgMembershipMapper.selectList(new LambdaQueryWrapper<OrgMembershipDO>()
                            .eq(OrgMembershipDO::getOrgUnitId, query.orgUnitId())
                            .eq(OrgMembershipDO::getStatus, ACTIVE))
                    .stream()
                    .map(OrgMembershipDO::getUserId)
                    .distinct()
                    .toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            wrapper.in(IamUserDO::getId, userIds);
        }

        Page<IamUserDO> result = iamUserMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return new PageResult<>(result.getTotal(), List.of());
        }

        List<Long> pageUserIds = result.getRecords().stream().map(IamUserDO::getId).toList();
        Map<Long, List<String>> orgUnitsByUserId = loadOrgUnitsByUserId(pageUserIds);
        Map<Long, List<String>> roleCodesByUserId = loadRoleCodesByUserId(pageUserIds);

        return new PageResult<>(
                result.getTotal(),
                result.getRecords().stream()
                        .map(item -> new IamUserAdminPageItem(
                                item.getId(),
                                item.getUserNo(),
                                item.getUserName(),
                                item.getStatus(),
                                orgUnitsByUserId.getOrDefault(item.getId(), List.of()),
                                roleCodesByUserId.getOrDefault(item.getId(), List.of()),
                                formatTime(item.getCreatedAt())
                        ))
                        .toList()
        );
    }

    private Map<Long, List<String>> loadOrgUnitsByUserId(List<Long> userIds) {
        List<OrgMembershipDO> memberships = orgMembershipMapper.selectList(new LambdaQueryWrapper<OrgMembershipDO>()
                .in(OrgMembershipDO::getUserId, userIds)
                .eq(OrgMembershipDO::getStatus, ACTIVE)
                .orderByAsc(OrgMembershipDO::getId));
        Map<Long, String> orgUnitNames = orgUnitMapper.selectBatchIds(
                        memberships.stream().map(OrgMembershipDO::getOrgUnitId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(OrgUnitDO::getId, OrgUnitDO::getUnitName));
        return memberships.stream().collect(Collectors.groupingBy(
                OrgMembershipDO::getUserId,
                Collectors.collectingAndThen(
                        Collectors.mapping(item -> orgUnitNames.get(item.getOrgUnitId()),
                                Collectors.toCollection(LinkedHashSet::new)),
                        List::copyOf
                )
        ));
    }

    private Map<Long, List<String>> loadRoleCodesByUserId(List<Long> userIds) {
        LocalDateTime now = LocalDateTime.now();
        List<IamUserRoleAssignmentDO> assignments = iamUserRoleAssignmentMapper.selectList(new LambdaQueryWrapper<IamUserRoleAssignmentDO>()
                .in(IamUserRoleAssignmentDO::getUserId, userIds)
                .eq(IamUserRoleAssignmentDO::getStatus, ACTIVE)
                .le(IamUserRoleAssignmentDO::getEffectiveFrom, now)
                .and(w -> w.isNull(IamUserRoleAssignmentDO::getEffectiveTo)
                        .or()
                        .gt(IamUserRoleAssignmentDO::getEffectiveTo, now))
                .orderByAsc(IamUserRoleAssignmentDO::getId));
        assignments = assignments.stream()
                .filter(item -> isRuntimeActive(item, now))
                .toList();
        Set<Long> roleIds = assignments.stream().map(IamUserRoleAssignmentDO::getRoleId).collect(Collectors.toSet());
        Map<Long, String> roleCodes = iamRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(IamRoleDO::getId, IamRoleDO::getRoleCode));
        return assignments.stream().collect(Collectors.groupingBy(
                IamUserRoleAssignmentDO::getUserId,
                Collectors.collectingAndThen(
                        Collectors.mapping(item -> roleCodes.get(item.getRoleId()),
                                Collectors.toCollection(LinkedHashSet::new)),
                        List::copyOf
                )
        ));
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : TIME_FORMATTER.format(value);
    }

    private boolean isRuntimeActive(IamUserRoleAssignmentDO assignment, LocalDateTime now) {
        return !assignment.getEffectiveFrom().isAfter(now)
                && (assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(now));
    }
}
