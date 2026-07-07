package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

@Service
public class ReviewApplicationAccessValidator {

    private final ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    public ReviewApplicationAccessValidator(ResourceScopeAccessEvaluator resourceScopeAccessEvaluator) {
        this.resourceScopeAccessEvaluator = resourceScopeAccessEvaluator;
    }

    public void requireAccess(UserAuthorizationContext reviewer, ReviewApplicationQueryRow row) {
        ScopeAccessDecision decision = resourceScopeAccessEvaluator.canAccessApplication(
                reviewer,
                AuthorizationPermissionCodes.APPLICATION_REVIEW,
                new ApplicationResourceContext(
                        row.getApplicationId(),
                        row.getApplicantUserId(),
                        row.getOrgUnitId(),
                        row.getOrgPath(),
                        row.getCategoryCode(),
                        row.getItemCode()
                )
        );
        if (!decision.isAllowed()) {
            throw new AccessDeniedAppException("当前审核人无权访问该申请");
        }
    }
}
