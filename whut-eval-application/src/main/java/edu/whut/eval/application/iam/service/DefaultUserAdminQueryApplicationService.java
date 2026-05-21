package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamUserAdminPageItem;
import edu.whut.eval.domain.iam.repository.UserAdminQueryRepository;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class DefaultUserAdminQueryApplicationService implements UserAdminQueryApplicationService {

    private static final Set<String> PAGEABLE_STATUS = Set.of("ACTIVE", "DISABLED", "LOCKED");

    private final UserAdminQueryRepository userAdminQueryRepository;
    private final OrgUnitLookupRepository orgUnitLookupRepository;

    public DefaultUserAdminQueryApplicationService(UserAdminQueryRepository userAdminQueryRepository,
                                                   OrgUnitLookupRepository orgUnitLookupRepository) {
        this.userAdminQueryRepository = userAdminQueryRepository;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
    }

    @Override
    public PageResult<UserAdminPageItemView> pageUsers(UserAdminPageQuery query) {
        validateQuery(query);
        PageResult<IamUserAdminPageItem> page = userAdminQueryRepository.pageUsers(
                new edu.whut.eval.domain.iam.query.UserAdminPageQuery(
                        query.pageNo(),
                        query.pageSize(),
                        normalize(query.keyword()),
                        normalize(query.status()),
                        query.orgUnitId()
                )
        );
        return new PageResult<>(
                page.total(),
                page.records().stream().map(this::toView).toList()
        );
    }

    private void validateQuery(UserAdminPageQuery query) {
        if (query.pageNo() <= 0) {
            throw new ValidationException("pageNo 必须大于 0");
        }
        if (query.pageSize() <= 0) {
            throw new ValidationException("pageSize 必须大于 0");
        }
        String status = normalize(query.status());
        if (status != null && !PAGEABLE_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE、DISABLED 或 LOCKED");
        }
        if (query.orgUnitId() != null) {
            orgUnitLookupRepository.findById(query.orgUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("组织不存在: " + query.orgUnitId()));
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserAdminPageItemView toView(IamUserAdminPageItem item) {
        return new UserAdminPageItemView(
                item.userId(),
                item.userNo(),
                item.userName(),
                item.status(),
                item.orgUnits(),
                item.roleCodes(),
                item.createdAt()
        );
    }
}
