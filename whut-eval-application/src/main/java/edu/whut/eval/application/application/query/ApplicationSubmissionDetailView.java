package edu.whut.eval.application.application.query;

import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ApplicationSubmissionDetailView {

    private Long applicationId;
    private Long orgUnitId;
    private String categoryCode;
    private String itemCode;
    private String academicYear;
    private String term;
    private String title;
    private String description;
    private ApplicationSubmissionStatus status;
    private Instant submittedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private String optionCode;
    private BigDecimal appliedPoints;
    private BigDecimal maxPoints;
    private int evidenceCount;
    private boolean exceedsMaxPoints;
    private String warningMessage;
    private List<ApplicationAttachmentView> attachments = List.of();

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
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

    public ApplicationSubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationSubmissionStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public boolean isExceedsMaxPoints() {
        return exceedsMaxPoints;
    }

    public void setExceedsMaxPoints(boolean exceedsMaxPoints) {
        this.exceedsMaxPoints = exceedsMaxPoints;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public List<ApplicationAttachmentView> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ApplicationAttachmentView> attachments) {
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
