package edu.whut.eval.domain.config.model;

/**
 * 平台规则配置。
 * 控制平台级别的功能开关和业务规则参数。
 */
public class PlatformRuleConfig {

    private boolean studentApplyEnabled;
    private boolean finalSubmitEnabled;
    private int maxReviewBatchSize;

    public boolean isStudentApplyEnabled() {
        return studentApplyEnabled;
    }

    public void setStudentApplyEnabled(boolean studentApplyEnabled) {
        this.studentApplyEnabled = studentApplyEnabled;
    }

    public boolean isFinalSubmitEnabled() {
        return finalSubmitEnabled;
    }

    public void setFinalSubmitEnabled(boolean finalSubmitEnabled) {
        this.finalSubmitEnabled = finalSubmitEnabled;
    }

    public int getMaxReviewBatchSize() {
        return maxReviewBatchSize;
    }

    public void setMaxReviewBatchSize(int maxReviewBatchSize) {
        this.maxReviewBatchSize = maxReviewBatchSize;
    }
}
