package edu.whut.eval.application.application.command;

import java.util.List;

/**
 * 更新申请草稿命令。
 */
public class UpdateApplicationDraftCommand {

    private final Long applicationId;
    private final String title;
    private final String description;
    private final List<String> attachmentFileIds;
    private final Long expectedVersion;

    public UpdateApplicationDraftCommand(Long applicationId,
                                         String title,
                                         String description,
                                         List<String> attachmentFileIds,
                                         Long expectedVersion) {
        this.applicationId = applicationId;
        this.title = title;
        this.description = description;
        this.attachmentFileIds = attachmentFileIds == null ? List.of() : List.copyOf(attachmentFileIds);
        this.expectedVersion = expectedVersion;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAttachmentFileIds() {
        return attachmentFileIds;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
