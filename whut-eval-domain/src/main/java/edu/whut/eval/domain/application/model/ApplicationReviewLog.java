package edu.whut.eval.domain.application.model;

import java.time.Instant;

public class ApplicationReviewLog {

    private final Long id;
    private final Long applicationId;
    private final ApplicationReviewAction action;
    private final Long reviewerId;
    private final String reviewRole;
    private final String reason;
    private final Instant reviewedAt;

    public ApplicationReviewLog(Long id,
                                Long applicationId,
                                ApplicationReviewAction action,
                                Long reviewerId,
                                String reviewRole,
                                String reason,
                                Instant reviewedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.action = action;
        this.reviewerId = reviewerId;
        this.reviewRole = reviewRole;
        this.reason = reason;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public ApplicationReviewAction getAction() {
        return action;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewRole() {
        return reviewRole;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
