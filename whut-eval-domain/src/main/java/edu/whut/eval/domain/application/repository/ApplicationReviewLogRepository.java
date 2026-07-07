package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.model.ApplicationReviewLog;

import java.util.List;

public interface ApplicationReviewLogRepository {

    ApplicationReviewLog append(ApplicationReviewLog reviewLog);

    List<ApplicationReviewLog> listByApplicationId(Long applicationId);
}
