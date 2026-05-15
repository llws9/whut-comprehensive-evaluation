package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新申请草稿请求。
 */
public class UpdateApplicationDraftRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotEmpty
    private List<@NotBlank String> attachmentFileIds;

    @NotNull
    private Long expectedVersion;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAttachmentFileIds() {
        return attachmentFileIds;
    }

    public void setAttachmentFileIds(List<String> attachmentFileIds) {
        this.attachmentFileIds = attachmentFileIds;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
