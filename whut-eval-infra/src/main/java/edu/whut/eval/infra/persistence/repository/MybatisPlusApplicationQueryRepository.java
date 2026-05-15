package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.ApplicationQueryMapper;
import edu.whut.eval.infra.persistence.query.ApplicationQueryRow;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 正式申请查询仓储实现：
 * 负责把访问上下文转换为授权层可理解的上下文，并把 scope translator 真正接入正式 Mapper。
 */
@Repository
public class MybatisPlusApplicationQueryRepository implements ApplicationQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ApplicationQueryMapper applicationQueryMapper;

    public MybatisPlusApplicationQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                 ScopePredicateBuilder scopePredicateBuilder,
                                                 ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                 ApplicationQueryMapper applicationQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.applicationQueryMapper = applicationQueryMapper;
    }

    /**
     * 正式申请仓储的核心闭环：evaluate -> predicate -> translator -> mapper。
     */
    @Override
    public PageResult<ApplicationRecord> pageAccessibleApplications(ApplicationAccessContext accessContext,
                                                                    ApplicationPageQuery query) {
        UserAuthorizationContext authorizationContext = toAuthorizationContext(accessContext);
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(
                authorizationContext,
                accessContext.getPermissionCode()
        );
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        SqlPredicateFragment fragment = applicationScopeSqlTranslator.translate(authorizationContext, predicate);

        long total = applicationQueryMapper.countAccessibleApplications(
                fragment.getExpression(),
                fragment.getParameters(),
                query
        );
        List<ApplicationRecord> records = applicationQueryMapper.selectAccessibleApplications(
                        fragment.getExpression(),
                        fragment.getParameters(),
                        query,
                        query.getOffset(),
                        query.getPageSize()
                ).stream()
                .map(this::toDomain)
                .toList();
        return new PageResult<>(total, records);
    }

    /**
     * 正式仓储不直接依赖 application service，因此在仓储内部完成访问上下文适配。
     */
    private UserAuthorizationContext toAuthorizationContext(ApplicationAccessContext accessContext) {
        return new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
    }

    /**
     * 统一把持久化查询行映射为领域结果对象。
     */
    private ApplicationRecord toDomain(ApplicationQueryRow row) {
        return new ApplicationRecord(
                row.getApplicationId(),
                row.getApplicantUserId(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getCategoryCode(),
                row.getItemCode()
        );
    }
}
