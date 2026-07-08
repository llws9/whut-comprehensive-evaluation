package edu.whut.eval.application.finalrecord.query;

import java.math.BigDecimal;
import java.time.Instant;

public class FinalRecordQueryRow {
    private Long finalRecordId;
    private Long studentUserId;
    private String studentUserNo;
    private String studentUserName;
    private Long orgUnitId;
    private String orgUnitName;
    private String orgPath;
    private String academicYear;
    private String status;
    private BigDecimal moralTotal;
    private BigDecimal intellectualTotal;
    private BigDecimal physicalTotal;
    private BigDecimal laborTotal;
    private BigDecimal grandTotal;
    private Instant submittedAt;
    private Instant confirmedAt;
    private String confirmComment;
    private Long version;

    public Long getFinalRecordId() { return finalRecordId; }
    public void setFinalRecordId(Long finalRecordId) { this.finalRecordId = finalRecordId; }
    public Long getStudentUserId() { return studentUserId; }
    public void setStudentUserId(Long studentUserId) { this.studentUserId = studentUserId; }
    public String getStudentUserNo() { return studentUserNo; }
    public void setStudentUserNo(String studentUserNo) { this.studentUserNo = studentUserNo; }
    public String getStudentUserName() { return studentUserName; }
    public void setStudentUserName(String studentUserName) { this.studentUserName = studentUserName; }
    public Long getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(Long orgUnitId) { this.orgUnitId = orgUnitId; }
    public String getOrgUnitName() { return orgUnitName; }
    public void setOrgUnitName(String orgUnitName) { this.orgUnitName = orgUnitName; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String orgPath) { this.orgPath = orgPath; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMoralTotal() { return moralTotal; }
    public void setMoralTotal(BigDecimal moralTotal) { this.moralTotal = moralTotal; }
    public BigDecimal getIntellectualTotal() { return intellectualTotal; }
    public void setIntellectualTotal(BigDecimal intellectualTotal) { this.intellectualTotal = intellectualTotal; }
    public BigDecimal getPhysicalTotal() { return physicalTotal; }
    public void setPhysicalTotal(BigDecimal physicalTotal) { this.physicalTotal = physicalTotal; }
    public BigDecimal getLaborTotal() { return laborTotal; }
    public void setLaborTotal(BigDecimal laborTotal) { this.laborTotal = laborTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getConfirmComment() { return confirmComment; }
    public void setConfirmComment(String confirmComment) { this.confirmComment = confirmComment; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
