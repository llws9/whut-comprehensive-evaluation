package edu.whut.eval.domain.application.service;

/**
 * 申请窗口校验策略。
 */
public interface ApplicationSubmissionWindowPolicy {

    /**
     * 当前申请在给定组织、项目和学期下是否允许提交。
     */
    boolean isWindowOpen(Long orgUnitId, String categoryCode, String itemCode, String academicYear, String term);
}
