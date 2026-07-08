package edu.whut.eval.application.auth.model;

public class FinalRecordResourceContext implements ScopeResourceContext {

    private final Long finalRecordId;
    private final Long studentUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String academicYear;

    public FinalRecordResourceContext(Long finalRecordId,
                                      Long studentUserId,
                                      Long orgUnitId,
                                      String orgPath,
                                      String academicYear) {
        this.finalRecordId = finalRecordId;
        this.studentUserId = studentUserId;
        this.orgUnitId = orgUnitId;
        this.orgPath = orgPath;
        this.academicYear = academicYear;
    }

    @Override
    public Long getOwnerUserId() {
        return studentUserId;
    }

    @Override
    public Long getOrgUnitId() {
        return orgUnitId;
    }

    @Override
    public String getOrgPath() {
        return orgPath;
    }

    @Override
    public String getCategoryCode() {
        return null;
    }

    @Override
    public String getItemCode() {
        return null;
    }

    @Override
    public Object getFieldValue(String fieldName) {
        if ("finalRecordId".equals(fieldName)) {
            return finalRecordId;
        }
        if ("studentUserId".equals(fieldName) || "ownerUserId".equals(fieldName)) {
            return studentUserId;
        }
        if ("orgUnitId".equals(fieldName)) {
            return orgUnitId;
        }
        if ("orgPath".equals(fieldName)) {
            return orgPath;
        }
        if ("academicYear".equals(fieldName)) {
            return academicYear;
        }
        return null;
    }
}
