package edu.whut.eval.application.score.service;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.score.query.ScoreRecordView;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.score.model.ScoreRecord;
import edu.whut.eval.domain.score.query.ScoreAccessContext;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.score.repository.ScoreQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

/**
 * 成绩查询应用服务：
 * 向上屏蔽授权上下文装配与仓储细节，向下统一驱动正式 ScoreQueryRepository。
 */
@Service
public class ScoreQueryApplicationService {
    static final String DEFAULT_SCORE_QUERY_PERMISSION = AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED;

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ScoreQueryRepository scoreQueryRepository;

    public ScoreQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                        ScoreQueryRepository scoreQueryRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.scoreQueryRepository = scoreQueryRepository;
    }

    /**
     * 查询当前用户在成绩查询权限下可见的成绩列表。
     */
    public PageResult<ScoreRecordView> pageAccessibleScores(ScorePageQuery query) {
        return pageAccessibleScores(query, DEFAULT_SCORE_QUERY_PERMISSION);
    }

    /**
     * 查询当前用户在指定成绩查询权限下可见的成绩列表。
     * 该入口用于支持 student/admin 在复用同一应用服务时声明不同的权限口径。
     */
    public PageResult<ScoreRecordView> pageAccessibleScores(ScorePageQuery query, String permissionCode) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ensurePermissionGranted(authorizationContext, permissionCode);
        PageResult<ScoreRecord> pageResult = scoreQueryRepository.pageAccessibleScores(
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
            throw new AccessDeniedAppException("当前用户无权限访问成绩列表");
        }
    }

    /**
     * 把运行时认证上下文转换成正式查询仓储消费的领域访问上下文。
     */
    private ScoreAccessContext toAccessContext(UserAuthorizationContext authorizationContext, String permissionCode) {
        return new ScoreAccessContext(
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
    private ScoreRecordView toView(ScoreRecord record) {
        return new ScoreRecordView(
                record.scoreId(),
                record.studentUserId(),
                record.orgUnitId(),
                record.orgPath(),
                record.categoryCode(),
                record.itemCode(),
                record.academicYear()
        );
    }
}
