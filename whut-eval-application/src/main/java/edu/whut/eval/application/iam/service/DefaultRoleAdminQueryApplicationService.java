package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.repository.RoleAdminQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class DefaultRoleAdminQueryApplicationService implements RoleAdminQueryApplicationService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED");

    private final RoleAdminQueryRepository roleAdminQueryRepository;

    public DefaultRoleAdminQueryApplicationService(RoleAdminQueryRepository roleAdminQueryRepository) {
        this.roleAdminQueryRepository = roleAdminQueryRepository;
    }

    @Override
    public PageResult<RoleAdminPageItemView> pageRoles(RoleAdminPageQuery query) {
        validateQuery(query);
        PageResult<IamRoleAdminPageItem> page = roleAdminQueryRepository.pageRoles(
                new edu.whut.eval.domain.iam.query.RoleAdminPageQuery(
                        query.pageNo(),
                        query.pageSize(),
                        normalize(query.keyword()),
                        normalize(query.status())
                )
        );
        return new PageResult<>(
                page.total(),
                page.records().stream().map(this::toView).toList()
        );
    }

    private void validateQuery(RoleAdminPageQuery query) {
        if (query.pageNo() <= 0) {
            throw new ValidationException("pageNo 必须大于 0");
        }
        if (query.pageSize() <= 0) {
            throw new ValidationException("pageSize 必须大于 0");
        }
        String status = normalize(query.status());
        if (status != null && !ALLOWED_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 DISABLED");
        }
    }

    private RoleAdminPageItemView toView(IamRoleAdminPageItem item) {
        return new RoleAdminPageItemView(
                item.roleId(),
                item.roleCode(),
                item.roleName(),
                item.roleScope(),
                item.status(),
                item.permissionCount(),
                item.createdAt()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
