package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationOverviewView;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;
import edu.whut.eval.domain.application.repository.ApplicationOverviewQueryRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

@Service
public class ApplicationOverviewQueryApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationOverviewQueryRepository applicationOverviewQueryRepository;

    public ApplicationOverviewQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                      ApplicationOverviewQueryRepository applicationOverviewQueryRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationOverviewQueryRepository = applicationOverviewQueryRepository;
    }

    public ApplicationOverviewView getCurrentStudentOverview() {
        UserAuthorizationContext context = userAuthorizationContextAssembler.requiredAuthorizationContext();
        return toView(applicationOverviewQueryRepository.getStudentOverview(context.getUserId()));
    }

    private ApplicationOverviewView toView(ApplicationOverviewSummary summary) {
        return new ApplicationOverviewView(
                summary.draftCount(),
                summary.submittedCount(),
                summary.returnedCount(),
                summary.approvedCount(),
                summary.rejectedCount(),
                summary.latestAcademicYear()
        );
    }
}
