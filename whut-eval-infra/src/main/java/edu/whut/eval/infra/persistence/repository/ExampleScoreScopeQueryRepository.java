package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.domain.auth.model.ScoreScopePredicate;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.ScoreScopePredicateBuilder;
import edu.whut.eval.infra.persistence.mapper.ExampleScoreScopeQueryMapper;
import edu.whut.eval.infra.persistence.query.ExampleScoreQueryRow;
import edu.whut.eval.infra.security.sql.ScoreScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;

import java.util.List;

/**
 * 示例成绩范围查询仓储：
 * 演示 Repository 如何把 evaluate -> predicate -> SQL translator -> mapper 串成完整链路。
 */
public class ExampleScoreScopeQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScoreScopePredicateBuilder scoreScopePredicateBuilder;
    private final ScoreScopeSqlTranslator scoreScopeSqlTranslator;
    private final ExampleScoreScopeQueryMapper exampleScoreScopeQueryMapper;

    public ExampleScoreScopeQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                            ScoreScopePredicateBuilder scoreScopePredicateBuilder,
                                            ScoreScopeSqlTranslator scoreScopeSqlTranslator,
                                            ExampleScoreScopeQueryMapper exampleScoreScopeQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scoreScopePredicateBuilder = scoreScopePredicateBuilder;
        this.scoreScopeSqlTranslator = scoreScopeSqlTranslator;
        this.exampleScoreScopeQueryMapper = exampleScoreScopeQueryMapper;
    }

    /**
     * 根据当前用户上下文和目标权限，查询示例成绩表中可访问的资源。
     */
    public List<ScoreResourceContext> listAccessibleScores(UserAuthorizationContext authorizationContext,
                                                           String permissionCode) {
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, permissionCode);
        ScoreScopePredicate predicate = scoreScopePredicateBuilder.build(authorizationContext, scopeSet);
        SqlPredicateFragment fragment = scoreScopeSqlTranslator.translate(authorizationContext, predicate);
        return exampleScoreScopeQueryMapper.selectAccessibleScores(
                        fragment.getExpression(),
                        fragment.getParameters()
                ).stream()
                .map(this::toResourceContext)
                .toList();
    }

    /**
     * 统一把 Mapper 行对象转换成授权层可复用的资源上下文。
     */
    private ScoreResourceContext toResourceContext(ExampleScoreQueryRow row) {
        return new ScoreResourceContext(
                row.getScoreId(),
                row.getStudentUserId(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getCategoryCode(),
                row.getItemCode(),
                row.getAcademicYear()
        );
    }
}
