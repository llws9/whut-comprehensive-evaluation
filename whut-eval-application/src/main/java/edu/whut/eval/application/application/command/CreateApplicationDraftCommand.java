package edu.whut.eval.application.application.command;

import java.util.List;

/**
 * 创建申请草稿命令。
 */
public class CreateApplicationDraftCommand {

    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;
    private final String term;
    private final String title;
    private final String description;
    private final List<String> attachmentFileIds;

    public CreateApplicationDraftCommand(Long orgUnitId,
                                         String categoryCode,
                                         String itemCode,
                                         String academicYear,
                                         String term,
                                         String title,
                                         String description,
                                         List<String> attachmentFileIds) {
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.academicYear = academicYear;
        this.term = term;
        this.title = title;
        this.description = description;
        this.attachmentFileIds = attachmentFileIds == null ? List.of() : List.copyOf(attachmentFileIds);
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public String getTerm() {
        return term;
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
}
