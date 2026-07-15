package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;

public interface ApplicationOverviewQueryRepository {

    ApplicationOverviewSummary getStudentOverview(Long applicantUserId);
}
