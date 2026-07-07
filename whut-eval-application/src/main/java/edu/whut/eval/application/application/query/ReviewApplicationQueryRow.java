package edu.whut.eval.application.application.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewApplicationQueryRow {

    private Long applicationId;
    private Long applicantUserId;
    private String applicantUserName;
    private String applicantUserNo;
    private Long orgUnitId;
    private String orgUnitName;
    private String orgPath;
    private String categoryCode;
    private String itemCode;
    private String academicYear;
    private String term;
    private String title;
    private String description;
    private String status;
    private LocalDateTime submittedAt;
    private Long version;
    private String optionCode;
    private BigDecimal appliedPoints;
    private BigDecimal maxPoints;
    private Integer evidenceCount;
    private Boolean exceedsMaxPoints;
    private String warningMessage;
    private String extraJson;
    private List<ReviewApplicationAttachmentRow> attachments = List.of();

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public String getApplicantUserName() {
        return applicantUserName;
    }

    public void setApplicantUserName(String applicantUserName) {
        this.applicantUserName = applicantUserName;
    }

    public String getApplicantUserNo() {
        return applicantUserNo;
    }

    public void setApplicantUserNo(String applicantUserNo) {
        this.applicantUserNo = applicantUserNo;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getOrgUnitName() {
        return orgUnitName;
    }

    public void setOrgUnitName(String orgUnitName) {
        this.orgUnitName = orgUnitName;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public void setOrgPath(String orgPath) {
        this.orgPath = orgPath;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getOptionCode() {
        return optionCode;
    }

    public void setOptionCode(String optionCode) {
        this.optionCode = optionCode;
    }

    public BigDecimal getAppliedPoints() {
        return appliedPoints;
    }

    public void setAppliedPoints(BigDecimal appliedPoints) {
        this.appliedPoints = appliedPoints;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(BigDecimal maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Boolean getExceedsMaxPoints() {
        return exceedsMaxPoints;
    }

    public void setExceedsMaxPoints(Boolean exceedsMaxPoints) {
        this.exceedsMaxPoints = exceedsMaxPoints;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public List<ReviewApplicationAttachmentRow> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ReviewApplicationAttachmentRow> attachments) {
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
