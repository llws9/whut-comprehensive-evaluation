package edu.whut.eval.application.auth.model;

public class ApplicationResourceContext implements ScopeResourceContext {

    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String categoryCode;
    private final String itemCode;

    public ApplicationResourceContext(Long applicationId,
                                      Long applicantUserId,
                                      Long orgUnitId,
                                      String orgPath,
                                      String categoryCode,
                                      String itemCode) {
        this.applicationId = applicationId;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.orgPath = orgPath;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    @Override
    public Long getOwnerUserId() {
        return applicantUserId;
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
        return categoryCode;
    }

    @Override
    public String getItemCode() {
        return itemCode;
    }

    @Override
    public Object getFieldValue(String fieldName) {
        if ("applicationId".equals(fieldName)) {
            return applicationId;
        }
        if ("applicantUserId".equals(fieldName) || "ownerUserId".equals(fieldName)) {
            return applicantUserId;
        }
        if ("orgUnitId".equals(fieldName)) {
            return orgUnitId;
        }
        if ("orgPath".equals(fieldName)) {
            return orgPath;
        }
        if ("categoryCode".equals(fieldName)) {
            return categoryCode;
        }
        if ("itemCode".equals(fieldName)) {
            return itemCode;
        }
        return null;
    }
}
