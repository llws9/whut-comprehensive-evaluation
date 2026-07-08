package edu.whut.eval.infra.persistence.dataobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinalRecordDO {
    private Long id;
    private Long studentUserId;
    private String academicYear;
    private String status;
    private BigDecimal moralTotal;
    private BigDecimal intellectualTotal;
    private BigDecimal physicalTotal;
    private BigDecimal laborTotal;
    private BigDecimal grandTotal;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private String confirmComment;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getMoralTotal() {
        return moralTotal;
    }

    public void setMoralTotal(BigDecimal moralTotal) {
        this.moralTotal = moralTotal;
    }

    public BigDecimal getIntellectualTotal() {
        return intellectualTotal;
    }

    public void setIntellectualTotal(BigDecimal intellectualTotal) {
        this.intellectualTotal = intellectualTotal;
    }

    public BigDecimal getPhysicalTotal() {
        return physicalTotal;
    }

    public void setPhysicalTotal(BigDecimal physicalTotal) {
        this.physicalTotal = physicalTotal;
    }

    public BigDecimal getLaborTotal() {
        return laborTotal;
    }

    public void setLaborTotal(BigDecimal laborTotal) {
        this.laborTotal = laborTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmComment() {
        return confirmComment;
    }

    public void setConfirmComment(String confirmComment) {
        this.confirmComment = confirmComment;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
