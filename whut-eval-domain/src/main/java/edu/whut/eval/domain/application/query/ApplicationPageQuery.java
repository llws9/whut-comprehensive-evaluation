package edu.whut.eval.domain.application.query;

/**
 * 正式申请列表查询对象。
 * 业务过滤条件与授权范围条件会在 Repository 中以 AND 方式组合。
 */
public class ApplicationPageQuery {

    private final long pageNo;
    private final long pageSize;
    private final Long applicationId;
    private final Long applicantUserId;
    private final Long orgUnitId;
    private final String categoryCode;
    private final String itemCode;

    public ApplicationPageQuery(long pageNo,
                                long pageSize,
                                Long applicationId,
                                Long applicantUserId,
                                Long orgUnitId,
                                String categoryCode,
                                String itemCode) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.applicationId = applicationId;
        this.applicantUserId = applicantUserId;
        this.orgUnitId = orgUnitId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
    }

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
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

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
