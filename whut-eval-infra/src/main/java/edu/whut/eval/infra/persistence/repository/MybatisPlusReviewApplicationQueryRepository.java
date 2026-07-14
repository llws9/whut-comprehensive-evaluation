package edu.whut.eval.infra.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.query.ReviewTaskSummaryCounts;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.domain.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.ReviewApplicationQueryMapper;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisPlusReviewApplicationQueryRepository implements ReviewApplicationQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final ReviewApplicationQueryMapper reviewApplicationQueryMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusReviewApplicationQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                       ScopePredicateBuilder scopePredicateBuilder,
                                                       ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                       ReviewApplicationQueryMapper reviewApplicationQueryMapper,
                                                       ObjectMapper objectMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.reviewApplicationQueryMapper = reviewApplicationQueryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<ReviewApplicationQueryRow> pageReviewApplications(ApplicationAccessContext accessContext,
                                                                        ReviewApplicationPageQuery query) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        long total = reviewApplicationQueryMapper.countReviewApplications(fragment.getExpression(), fragment.getParameters(), query);
        List<ReviewApplicationQueryRow> records = reviewApplicationQueryMapper.selectReviewApplications(
                        fragment.getExpression(),
                        fragment.getParameters(),
                        query,
                        query.getOffset(),
                        query.getPageSize()
                )
                .stream()
                .peek(this::materializeScoringSnapshot)
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    public Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(Long applicationId) {
        ReviewApplicationQueryRow row = reviewApplicationQueryMapper.selectReviewApplicationDetail(
                null,
                null,
                applicationId
        );
        if (row == null) {
            return Optional.empty();
        }
        materializeScoringSnapshot(row);
        row.setAttachments(reviewApplicationQueryMapper.selectAttachments(applicationId));
        return Optional.of(row);
    }

    @Override
    public Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(ApplicationAccessContext accessContext,
                                                                          Long applicationId) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        ReviewApplicationQueryRow row = reviewApplicationQueryMapper.selectReviewApplicationDetail(
                fragment.getExpression(),
                fragment.getParameters(),
                applicationId
        );
        if (row == null) {
            return Optional.empty();
        }
        materializeScoringSnapshot(row);
        row.setAttachments(reviewApplicationQueryMapper.selectAttachments(applicationId));
        return Optional.of(row);
    }

    @Override
    public ReviewTaskSummaryCounts countReviewTaskSummary(ApplicationAccessContext accessContext,
                                                         LocalDateTime dayStart,
                                                         LocalDateTime dayEnd) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        ReviewTaskSummaryCounts counts = reviewApplicationQueryMapper.countReviewTaskSummary(
                fragment.getExpression(),
                fragment.getParameters(),
                accessContext.getUserId(),
                dayStart,
                dayEnd
        );
        return counts == null ? new ReviewTaskSummaryCounts(0, 0, 0, 0) : counts;
    }

    private SqlPredicateFragment scopeFragment(ApplicationAccessContext accessContext) {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, accessContext.getPermissionCode());
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        return applicationScopeSqlTranslator.translate(authorizationContext, predicate);
    }

    private void materializeScoringSnapshot(ReviewApplicationQueryRow row) {
        if (row.getExtraJson() == null || row.getExtraJson().isBlank()) {
            return;
        }
        try {
            JsonNode extra = objectMapper.readTree(row.getExtraJson());
            row.setOptionCode(extra.path("optionCode").asText(null));
            String maxPointsText = extra.path("maxPoints").asText(null);
            row.setMaxPoints(maxPointsText == null || maxPointsText.isBlank() ? null : new BigDecimal(maxPointsText));
            row.setExceedsMaxPoints(extra.path("exceedsMaxPoints").asBoolean(false));
        } catch (Exception exception) {
            throw new IllegalStateException("申请评分快照解析失败: applicationId=" + row.getApplicationId(), exception);
        }
    }
}
