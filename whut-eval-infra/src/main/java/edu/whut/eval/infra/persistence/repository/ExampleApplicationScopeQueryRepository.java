package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.ScopePredicateBuilder;
import edu.whut.eval.infra.persistence.mapper.ExampleApplicationScopeQueryMapper;
import edu.whut.eval.infra.persistence.query.ExampleApplicationQueryRow;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;

import java.util.List;

/**
 * 示例申请范围查询仓储：
 * 演示 Repository 如何把 evaluate -> predicate -> SQL translator -> mapper 串成完整链路。
 */
public class ExampleApplicationScopeQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ExampleApplicationScopeQueryMapper exampleApplicationScopeQueryMapper;

    public ExampleApplicationScopeQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                  ScopePredicateBuilder scopePredicateBuilder,
                                                  ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                  ExampleApplicationScopeQueryMapper exampleApplicationScopeQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.exampleApplicationScopeQueryMapper = exampleApplicationScopeQueryMapper;
    }

    /**
     * 根据当前用户上下文和目标权限，查询示例申请表中可访问的资源。
     */
    public List<ApplicationResourceContext> listAccessibleApplications(UserAuthorizationContext authorizationContext,
                                                                       String permissionCode) {
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, permissionCode);
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        SqlPredicateFragment fragment = applicationScopeSqlTranslator.translate(authorizationContext, predicate);
        return exampleApplicationScopeQueryMapper.selectAccessibleApplications(
                        fragment.getExpression(),
                        fragment.getParameters()
                ).stream()
                .map(this::toResourceContext)
                .toList();
    }

    /**
     * 统一把 Mapper 行对象转换成授权层可复用的资源上下文。
     */
    private ApplicationResourceContext toResourceContext(ExampleApplicationQueryRow row) {
        return new ApplicationResourceContext(
                row.getApplicationId(),
                row.getApplicantUserId(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getCategoryCode(),
                row.getItemCode()
        );
    }
}
