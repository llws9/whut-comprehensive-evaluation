package edu.whut.eval.infra.nacos.model.typed;

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
