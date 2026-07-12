package edu.whut.eval.infra.persistence.repository.row;

public class MentorScoreImportStudentTargetRow {
    private Long studentUserId;
    private String studentNo;
    private Long orgUnitId;
    private String orgPath;
    private String finalRecordStatus;

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public void setOrgPath(String orgPath) {
        this.orgPath = orgPath;
    }

    public String getFinalRecordStatus() {
        return finalRecordStatus;
    }

    public void setFinalRecordStatus(String finalRecordStatus) {
        this.finalRecordStatus = finalRecordStatus;
    }
}
