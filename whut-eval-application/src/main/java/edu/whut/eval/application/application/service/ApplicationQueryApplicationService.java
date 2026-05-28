package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

/**
 * 申请查询应用服务：
 * 向上屏蔽授权上下文装配与仓储细节，向下统一驱动正式 ApplicationQueryRepository。
 */
@Service
public class ApplicationQueryApplicationService {
    static final String DEFAULT_APPLICATION_QUERY_PERMISSION = AuthorizationPermissionCodes.APPLICATION_REVIEW;

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationQueryRepository applicationQueryRepository;

    public ApplicationQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                              ApplicationQueryRepository applicationQueryRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationQueryRepository = applicationQueryRepository;
    }

    /**
     * 查询当前用户在申请查询权限下可见的申请列表。
     */
    public PageResult<ApplicationRecordView> pageAccessibleApplications(ApplicationPageQuery query) {
        return pageAccessibleApplications(query, DEFAULT_APPLICATION_QUERY_PERMISSION);
    }

    /**
     * 查询当前用户在指定申请查询权限下可见的申请列表。
     * 该入口用于支持 student/admin 在复用同一应用服务时声明不同的权限口径。
     */
    public PageResult<ApplicationRecordView> pageAccessibleApplications(ApplicationPageQuery query, String permissionCode) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermissionGranted(authorizationContext, permissionCode);
        PageResult<ApplicationRecord> pageResult = applicationQueryRepository.pageAccessibleApplications(
                toAccessContext(authorizationContext, permissionCode),
                query
        );
        return new PageResult<>(
                pageResult.total(),
                pageResult.records().stream().map(this::toView).toList()
        );
    }

    /**
     * 应用服务对“没有权限访问列表”走显式拒绝，而不是把无权限误表现为空数据。
     */
    private void ensurePermissionGranted(UserAuthorizationContext authorizationContext, String permissionCode) {
        if (!authorizationContext.hasAuthority(permissionCode)) {
            throw new AccessDeniedAppException("当前用户无权限访问申请列表");
        }
    }

    /**
     * 把运行时认证上下文转换成正式查询仓储消费的领域访问上下文。
     */
    private ApplicationAccessContext toAccessContext(UserAuthorizationContext authorizationContext, String permissionCode) {
        return new ApplicationAccessContext(
                authorizationContext.getUserId(),
                authorizationContext.getUserNo(),
                authorizationContext.getUserName(),
                authorizationContext.getIdentity(),
                authorizationContext.getRoles(),
                authorizationContext.getAuthorities(),
                authorizationContext.getScopeRules(),
                permissionCode
        );
    }

    /**
     * 统一把领域结果映射为对外查询视图。
     */
    private ApplicationRecordView toView(ApplicationRecord record) {
        return new ApplicationRecordView(
                record.applicationId(),
                record.applicantUserId(),
                record.orgUnitId(),
                record.orgPath(),
                record.categoryCode(),
                record.itemCode()
        );
    }
}
