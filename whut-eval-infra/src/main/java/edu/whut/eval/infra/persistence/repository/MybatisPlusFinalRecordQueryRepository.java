package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentRow;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.finalrecord.service.FinalRecordScopePredicateBuilder;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.FinalRecordQueryMapper;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisPlusFinalRecordQueryRepository implements FinalRecordQueryRepository {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final FinalRecordScopePredicateBuilder finalRecordScopePredicateBuilder = new FinalRecordScopePredicateBuilder();
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;
    private final FinalRecordQueryMapper finalRecordQueryMapper;

    public MybatisPlusFinalRecordQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                 ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                 FinalRecordQueryMapper finalRecordQueryMapper) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
        this.finalRecordQueryMapper = finalRecordQueryMapper;
    }

    @Override
    public Optional<FinalRecordQueryRow> findStudentFinalRecord(long studentUserId, String academicYear) {
        return Optional.ofNullable(finalRecordQueryMapper.selectStudentFinalRecord(studentUserId, academicYear));
    }

    @Override
    public List<FinalComponentScoreRow> listStudentFinalRecordComponents(long finalRecordId) {
        return finalRecordQueryMapper.selectComponents(finalRecordId);
    }

    @Override
    public PageResult<FinalRecordQueryRow> pageAdminFinalRecords(FinalRecordAccessContext accessContext,
                                                                 FinalRecordPageQuery query) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        long total = finalRecordQueryMapper.countAdminFinalRecords(fragment.getExpression(), fragment.getParameters(), query);
        List<FinalRecordQueryRow> records = finalRecordQueryMapper.selectAdminFinalRecords(
                fragment.getExpression(),
                fragment.getParameters(),
                query,
                query.getOffset(),
                query.getPageSize()
        );
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<UnsubmittedStudentRow> pageUnsubmittedStudents(FinalRecordAccessContext accessContext,
                                                                     UnsubmittedFinalRecordQuery query) {
        throw new UnsupportedOperationException("D-11 unsubmitted final-record repository SQL is implemented in Task 3");
    }

    @Override
    public Optional<FinalRecordQueryRow> findAdminFinalRecordDetail(long finalRecordId) {
        return Optional.ofNullable(finalRecordQueryMapper.selectAdminFinalRecordDetail(finalRecordId));
    }

    @Override
    public List<FinalComponentScoreRow> listAdminFinalRecordComponents(long finalRecordId) {
        return finalRecordQueryMapper.selectComponents(finalRecordId);
    }

    private SqlPredicateFragment scopeFragment(FinalRecordAccessContext accessContext) {
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
        ApplicationScopePredicate predicate = finalRecordScopePredicateBuilder.buildForFinalRecord(authorizationContext, scopeSet);
        return applicationScopeSqlTranslator.translate(authorizationContext, predicate);
    }
}
