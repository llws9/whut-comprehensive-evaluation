package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.ScoreScopePredicate;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.ScoreScopePredicateBuilder;
import edu.whut.eval.domain.score.model.ScoreRecord;
import edu.whut.eval.domain.score.query.ScoreAccessContext;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.score.repository.ScoreQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.ScoreQueryMapper;
import edu.whut.eval.infra.persistence.query.ScoreQueryRow;
import edu.whut.eval.infra.security.sql.ScoreScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 正式成绩查询仓储实现：
 * 负责把访问上下文转换为授权层可理解的上下文，并把 score translator 真正接入正式 Mapper。
 */
@Repository
public class MybatisPlusScoreQueryRepository implements ScoreQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScoreScopePredicateBuilder scoreScopePredicateBuilder;
    private final ScoreScopeSqlTranslator scoreScopeSqlTranslator;
    private final ScoreQueryMapper scoreQueryMapper;

    public MybatisPlusScoreQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                           ScoreScopePredicateBuilder scoreScopePredicateBuilder,
                                           ScoreScopeSqlTranslator scoreScopeSqlTranslator,
                                           ScoreQueryMapper scoreQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scoreScopePredicateBuilder = scoreScopePredicateBuilder;
        this.scoreScopeSqlTranslator = scoreScopeSqlTranslator;
        this.scoreQueryMapper = scoreQueryMapper;
    }

    /**
     * 正式成绩仓储的核心闭环：evaluate -> predicate -> translator -> mapper。
     */
    @Override
    public PageResult<ScoreRecord> pageAccessibleScores(ScoreAccessContext accessContext,
                                                        ScorePageQuery query) {
        UserAuthorizationContext authorizationContext = toAuthorizationContext(accessContext);
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(
                authorizationContext,
                accessContext.getPermissionCode()
        );
        ScoreScopePredicate predicate = scoreScopePredicateBuilder.build(authorizationContext, scopeSet);
        SqlPredicateFragment fragment = scoreScopeSqlTranslator.translate(authorizationContext, predicate);

        long total = scoreQueryMapper.countAccessibleScores(
                fragment.getExpression(),
                fragment.getParameters(),
                query
        );
        List<ScoreRecord> records = scoreQueryMapper.selectAccessibleScores(
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
    private UserAuthorizationContext toAuthorizationContext(ScoreAccessContext accessContext) {
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
    private ScoreRecord toDomain(ScoreQueryRow row) {
        return new ScoreRecord(
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
