package edu.whut.eval.application.finalrecord.service;

import edu.whut.eval.application.auth.model.FinalRecordResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

@Service
public class FinalRecordAccessValidator {

    private final ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    public FinalRecordAccessValidator(ResourceScopeAccessEvaluator resourceScopeAccessEvaluator) {
        this.resourceScopeAccessEvaluator = resourceScopeAccessEvaluator;
    }

    public void requireAccess(UserAuthorizationContext authorizationContext,
                              FinalRecordQueryRow row,
                              String permissionCode) {
        FinalRecordResourceContext resourceContext = new FinalRecordResourceContext(
                row.getFinalRecordId(),
                row.getStudentUserId(),
                row.getOrgUnitId(),
                row.getOrgPath(),
                row.getAcademicYear()
        );
        ScopeAccessDecision decision = resourceScopeAccessEvaluator.canAccessFinalRecord(
                authorizationContext,
                permissionCode,
                resourceContext
        );
        if (!decision.isAllowed()) {
            throw new AccessDeniedAppException("当前用户无权访问该最终成绩");
        }
    }
}
