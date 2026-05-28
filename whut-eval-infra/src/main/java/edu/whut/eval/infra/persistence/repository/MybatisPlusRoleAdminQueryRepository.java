package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.query.RoleAdminPageQuery;
import edu.whut.eval.domain.iam.repository.RoleAdminQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusRoleAdminQueryRepository implements RoleAdminQueryRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IamRoleMapper iamRoleMapper;
    private final IamRolePermissionMapper iamRolePermissionMapper;

    public MybatisPlusRoleAdminQueryRepository(IamRoleMapper iamRoleMapper,
                                               IamRolePermissionMapper iamRolePermissionMapper) {
        this.iamRoleMapper = iamRoleMapper;
        this.iamRolePermissionMapper = iamRolePermissionMapper;
    }

    @Override
    public PageResult<IamRoleAdminPageItem> pageRoles(RoleAdminPageQuery query) {
        Page<IamRoleDO> page = Page.of(query.pageNo(), query.pageSize());
        LambdaQueryWrapper<IamRoleDO> wrapper = new LambdaQueryWrapper<IamRoleDO>()
                .and(query.keyword() != null, q -> q.like(IamRoleDO::getRoleCode, query.keyword())
                        .or()
                        .like(IamRoleDO::getRoleName, query.keyword()))
                .eq(query.status() != null, IamRoleDO::getStatus, query.status())
                .orderByAsc(IamRoleDO::getId);
        Page<IamRoleDO> result = iamRoleMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return new PageResult<>(result.getTotal(), List.of());
        }
        Map<Long, Integer> permissionCountByRoleId = countPermissions(result.getRecords().stream().map(IamRoleDO::getId).toList());
        return new PageResult<>(
                result.getTotal(),
                result.getRecords().stream()
                        .map(item -> new IamRoleAdminPageItem(
                                item.getId(),
                                item.getRoleCode(),
                                item.getRoleName(),
                                item.getRoleScope(),
                                item.getStatus(),
                                permissionCountByRoleId.getOrDefault(item.getId(), 0),
                                formatTime(item.getCreatedAt())
                        ))
                        .toList()
        );
    }

    private Map<Long, Integer> countPermissions(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return iamRolePermissionMapper.selectList(new LambdaQueryWrapper<IamRolePermissionDO>()
                        .in(IamRolePermissionDO::getRoleId, roleIds))
                .stream()
                .collect(Collectors.groupingBy(IamRolePermissionDO::getRoleId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : TIME_FORMATTER.format(value);
    }
}
