package edu.whut.eval.domain.application.model;

/**
 * 申请提交状态。
 */
public enum ApplicationSubmissionStatus {
    DRAFT,
    SUBMITTED,
    RETURNED,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    /**
     * 当前状态是否允许学生继续编辑。
     */
    public boolean editableByStudent() {
        return this == DRAFT || this == RETURNED;
    }
}
