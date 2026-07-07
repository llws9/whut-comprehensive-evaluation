package edu.whut.eval.domain.application.model;

import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;

import java.time.Instant;
import java.util.List;

/**
 * 学生侧综测申请聚合根。
 */
public class ApplicationSubmission {

    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;
    private final String academicYear;
    private final String term;
    private final String title;
    private final String description;
    private final List<AttachmentRef> evidenceAttachments;
    private final ApplicationSubmissionStatus status;
    private final Instant submittedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long version;
    private final ApplicationScoringSnapshot scoringSnapshot;

    public ApplicationSubmission(Long applicationId,
                                 Long applicantUserId,
                                 Long orgUnitId,
                                 String categoryCode,
                                 String itemCode,
                                 String academicYear,
                                 String term,
                                 String title,
                                 String description,
                                 List<AttachmentRef> evidenceAttachments,
                                 ApplicationSubmissionStatus status,
                                 Instant submittedAt,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Long version) {
        this(applicationId, applicantUserId, orgUnitId, categoryCode, itemCode, academicYear, term, title, description,
                evidenceAttachments, status, submittedAt, createdAt, updatedAt, version, null);
    }

    public ApplicationSubmission(Long applicationId,
                                 Long applicantUserId,
                                 Long orgUnitId,
                                 String categoryCode,
                                 String itemCode,
                                 String academicYear,
                                 String term,
                                 String title,
                                 String description,
                                 List<AttachmentRef> evidenceAttachments,
                                 ApplicationSubmissionStatus status,
                                 Instant submittedAt,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Long version,
                                 ApplicationScoringSnapshot scoringSnapshot) {
        this.applicationId = applicationId;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.academicYear = academicYear;
        this.term = term;
        this.title = title;
        this.description = description;
        this.evidenceAttachments = evidenceAttachments == null ? List.of() : List.copyOf(evidenceAttachments);
        this.status = status;
        this.submittedAt = submittedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.scoringSnapshot = scoringSnapshot;
    }

    /**
     * 创建草稿状态的申请聚合。
     */
    public static ApplicationSubmission createDraft(Long applicantUserId,
                                                    Long orgUnitId,
                                                    String categoryCode,
                                                    String itemCode,
                                                    String academicYear,
                                                    String term,
                                                    String title,
                                                    String description,
                                                    List<AttachmentRef> attachments) {
        Instant now = Instant.now();
        return new ApplicationSubmission(
                null,
                applicantUserId,
                orgUnitId,
                categoryCode,
                itemCode,
                academicYear,
                term,
                title,
                description,
                attachments,
                ApplicationSubmissionStatus.DRAFT,
                null,
                now,
                now,
                0L
        );
    }

    /**
     * 在草稿或退回状态下更新申请内容与附件。
     */
    public ApplicationSubmission updateDraft(String newTitle,
                                             String newDescription,
                                             List<AttachmentRef> attachments,
                                             long expectedVersion) {
        assertEditable();
        assertExpectedVersion(expectedVersion);
        return new ApplicationSubmission(
                applicationId,
                applicantUserId,
                orgUnitId,
                categoryCode,
                itemCode,
                academicYear,
                term,
                newTitle,
                newDescription,
                attachments,
                status,
                submittedAt,
                createdAt,
                Instant.now(),
                version + 1,
                scoringSnapshot
        );
    }

    /**
     * 将申请提交到待审核状态。
     */
    public ApplicationSubmission submit(long expectedVersion) {
        throw new ValidationException("申请评分快照不能为空");
    }

    /**
     * 将申请连同提交时评分快照提交到待审核状态。
     */
    public ApplicationSubmission submit(long expectedVersion, ApplicationScoringSnapshot scoringSnapshot) {
        assertEditable();
        assertExpectedVersion(expectedVersion);
        if (title == null || title.isBlank()) {
            throw new ValidationException("申请标题不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("申请说明不能为空");
        }
        if (evidenceAttachments.isEmpty()) {
            throw new ValidationException("申请附件不能为空");
        }
        if (scoringSnapshot == null) {
            throw new ValidationException("申请评分快照不能为空");
        }
        Instant now = Instant.now();
        return new ApplicationSubmission(
                applicationId,
                applicantUserId,
                orgUnitId,
                categoryCode,
                itemCode,
                academicYear,
                term,
                title,
                description,
                evidenceAttachments,
                ApplicationSubmissionStatus.SUBMITTED,
                now,
                createdAt,
                now,
                version + 1,
                scoringSnapshot
        );
    }

    /**
     * 学生主动撤回当前申请。
     */
    public ApplicationSubmission withdraw(long expectedVersion) {
        assertWithdrawable();
        assertExpectedVersion(expectedVersion);
        return new ApplicationSubmission(
                applicationId,
                applicantUserId,
                orgUnitId,
                categoryCode,
                itemCode,
                academicYear,
                term,
                title,
                description,
                evidenceAttachments,
                ApplicationSubmissionStatus.WITHDRAWN,
                submittedAt,
                createdAt,
                Instant.now(),
                version + 1,
                scoringSnapshot
        );
    }

    private void assertWithdrawable() {
        if (status != ApplicationSubmissionStatus.SUBMITTED) {
            throw new ValidationException("当前申请状态不允许撤回");
        }
    }

    private void assertEditable() {
        if (!status.editableByStudent()) {
            throw new ValidationException("当前申请状态不允许学生编辑");
        }
    }

    private void assertExpectedVersion(long expectedVersion) {
        if (version == null || version.longValue() != expectedVersion) {
            throw new ConflictException("申请版本已变更，请刷新后重试");
        }
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
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

    public List<AttachmentRef> getEvidenceAttachments() {
        return evidenceAttachments;
    }

    public ApplicationSubmissionStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public ApplicationScoringSnapshot getScoringSnapshot() {
        return scoringSnapshot;
    }
}
