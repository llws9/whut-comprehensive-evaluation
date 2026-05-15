package edu.whut.eval.domain.application.service;

/**
 * 活跃申请冲突校验策略。
 */
public interface ActiveSubmissionPolicy {

    /**
     * 判断当前学生在相同项目和学期下是否已存在活跃申请。
     */
    boolean hasActiveSubmission(Long applicantUserId,
                                String itemCode,
                                String academicYear,
                                String term,
                                Long excludeApplicationId);
}
