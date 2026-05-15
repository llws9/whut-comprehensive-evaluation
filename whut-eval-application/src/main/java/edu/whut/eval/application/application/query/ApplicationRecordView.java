package edu.whut.eval.application.application.query;

/**
 * 申请列表对外查询视图。
 */
public class ApplicationRecordView {

    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String orgPath;
    private final String categoryCode;
    private final String itemCode;

    public ApplicationRecordView(Long applicationId,
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

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getOrgPath() {
        return orgPath;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }
}
