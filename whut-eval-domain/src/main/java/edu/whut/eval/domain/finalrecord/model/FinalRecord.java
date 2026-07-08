package edu.whut.eval.domain.finalrecord.model;

import edu.whut.eval.common.exception.ConflictException;

import java.math.BigDecimal;
import java.time.Instant;

public class FinalRecord {

    private final Long id;
    private final Long studentUserId;
    private final String academicYear;
    private final FinalRecordStatus status;
    private final BigDecimal moralTotal;
    private final BigDecimal intellectualTotal;
    private final BigDecimal physicalTotal;
    private final BigDecimal laborTotal;
    private final BigDecimal grandTotal;
    private final Instant submittedAt;
    private final Instant confirmedAt;
    private final String confirmComment;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public FinalRecord(Long id,
                       Long studentUserId,
                       String academicYear,
                       FinalRecordStatus status,
                       BigDecimal moralTotal,
                       BigDecimal intellectualTotal,
                       BigDecimal physicalTotal,
                       BigDecimal laborTotal,
                       BigDecimal grandTotal,
                       Instant submittedAt,
                       Instant confirmedAt,
                       String confirmComment,
                       Long version,
                       Instant createdAt,
                       Instant updatedAt) {
        this.id = id;
        this.studentUserId = studentUserId;
        this.academicYear = academicYear;
        this.status = status;
        this.moralTotal = moralTotal;
        this.intellectualTotal = intellectualTotal;
        this.physicalTotal = physicalTotal;
        this.laborTotal = laborTotal;
        this.grandTotal = grandTotal;
        this.submittedAt = submittedAt;
        this.confirmedAt = confirmedAt;
        this.confirmComment = confirmComment;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FinalRecord createDraft(Long id,
                                          Long studentUserId,
                                          String academicYear,
                                          BigDecimal moralTotal,
                                          BigDecimal intellectualTotal,
                                          BigDecimal physicalTotal,
                                          BigDecimal laborTotal,
                                          BigDecimal grandTotal,
                                          Instant now) {
        return new FinalRecord(id, studentUserId, academicYear, FinalRecordStatus.DRAFT,
                moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal,
                null, null, null, 0L, now, now);
    }

    public FinalRecord submit(long expectedVersion) {
        assertVersion(expectedVersion);
        if (status != FinalRecordStatus.DRAFT) {
            throw new ConflictException("最终成绩只能从草稿状态提交");
        }
        Instant now = Instant.now();
        return new FinalRecord(id, studentUserId, academicYear, FinalRecordStatus.SUBMITTED,
                moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal,
                now, confirmedAt, confirmComment, version + 1, createdAt, now);
    }

    public FinalRecord confirm(long expectedVersion, String confirmComment) {
        assertVersion(expectedVersion);
        if (status != FinalRecordStatus.SUBMITTED) {
            throw new ConflictException("只能确认已提交的最终成绩");
        }
        Instant now = Instant.now();
        return new FinalRecord(id, studentUserId, academicYear, FinalRecordStatus.CONFIRMED,
                moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal,
                submittedAt, now, confirmComment, version + 1, createdAt, now);
    }

    private void assertVersion(long expectedVersion) {
        if (version == null || version.longValue() != expectedVersion) {
            throw new ConflictException("最终成绩版本已变更，请刷新后重试");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public FinalRecordStatus getStatus() {
        return status;
    }

    public BigDecimal getMoralTotal() {
        return moralTotal;
    }

    public BigDecimal getIntellectualTotal() {
        return intellectualTotal;
    }

    public BigDecimal getPhysicalTotal() {
        return physicalTotal;
    }

    public BigDecimal getLaborTotal() {
        return laborTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getConfirmComment() {
        return confirmComment;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
