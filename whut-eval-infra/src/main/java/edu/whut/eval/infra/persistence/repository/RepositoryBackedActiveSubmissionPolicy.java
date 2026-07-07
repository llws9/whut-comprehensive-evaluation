package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.infra.persistence.mapper.ApplicationSubmissionMapper;
import org.springframework.stereotype.Component;

/**
 * 基于申请主表查询的活跃申请冲突策略。
 */
@Component
public class RepositoryBackedActiveSubmissionPolicy implements ActiveSubmissionPolicy {

    private final ApplicationSubmissionMapper applicationSubmissionMapper;

    public RepositoryBackedActiveSubmissionPolicy(ApplicationSubmissionMapper applicationSubmissionMapper) {
        this.applicationSubmissionMapper = applicationSubmissionMapper;
    }

    @Override
    public boolean hasActiveSubmission(Long applicantUserId,
                                       String itemCode,
                                       String academicYear,
                                       String term,
                                       Long excludeApplicationId) {
        return applicationSubmissionMapper.countActiveSubmission(
                applicantUserId,
                itemCode,
                academicYear,
                term,
                excludeApplicationId
        ) > 0;
    }
}
