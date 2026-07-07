package edu.whut.eval.interfaces.review.request;

import jakarta.validation.constraints.NotNull;

public class ApproveReviewRequest {

    @NotNull
    private Long expectedVersion;

    private String comment;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
