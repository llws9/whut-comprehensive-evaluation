package edu.whut.eval.interfaces.review.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchApproveReviewRequest {

    @NotEmpty
    private List<Long> applicationIds;

    private String comment;

    public List<Long> getApplicationIds() {
        return applicationIds;
    }

    public void setApplicationIds(List<Long> applicationIds) {
        this.applicationIds = applicationIds;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
