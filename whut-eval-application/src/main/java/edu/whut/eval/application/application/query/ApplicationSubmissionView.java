package edu.whut.eval.application.application.query;

import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;

import java.math.BigDecimal;

/**
 * 申请写接口返回视图。
 */
public class ApplicationSubmissionView {

    private final Long applicationId;
    private final ApplicationSubmissionStatus status;
    private final String title;
    private final String description;
    private final int attachmentCount;
    private final Long version;
    private final BigDecimal appliedPoints;
    private final BigDecimal maxPoints;
    private final boolean exceedsMaxPoints;
    private final String warningMessage;

    public ApplicationSubmissionView(Long applicationId,
                                     ApplicationSubmissionStatus status,
                                     String title,
                                     String description,
                                     int attachmentCount,
                                     Long version) {
        this(applicationId, status, title, description, attachmentCount, version, null, null, false, null);
    }

    public ApplicationSubmissionView(Long applicationId,
                                     ApplicationSubmissionStatus status,
                                     String title,
                                     String description,
                                     int attachmentCount,
                                     Long version,
                                     BigDecimal appliedPoints,
                                     BigDecimal maxPoints,
                                     boolean exceedsMaxPoints,
                                     String warningMessage) {
        this.applicationId = applicationId;
        this.status = status;
        this.title = title;
        this.description = description;
        this.attachmentCount = attachmentCount;
        this.version = version;
        this.appliedPoints = appliedPoints;
        this.maxPoints = maxPoints;
        this.exceedsMaxPoints = exceedsMaxPoints;
        this.warningMessage = warningMessage;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public ApplicationSubmissionStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public Long getVersion() {
        return version;
    }

    public BigDecimal getAppliedPoints() {
        return appliedPoints;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public boolean isExceedsMaxPoints() {
        return exceedsMaxPoints;
    }

    public String getWarningMessage() {
        return warningMessage;
    }
}
