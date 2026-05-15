package edu.whut.eval.application.application.query;

import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;

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

    public ApplicationSubmissionView(Long applicationId,
                                     ApplicationSubmissionStatus status,
                                     String title,
                                     String description,
                                     int attachmentCount,
                                     Long version) {
        this.applicationId = applicationId;
        this.status = status;
        this.title = title;
        this.description = description;
        this.attachmentCount = attachmentCount;
        this.version = version;
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
}
