package edu.whut.eval.domain.application.query;

public class ReviewApplicationPageQuery {

    private final long pageNo;
    private final long pageSize;
    private final String academicYear;
    private final String categoryCode;
    private final String itemCode;
    private final String status;
    private final String keyword;
    private final Long orgUnitId;

    public ReviewApplicationPageQuery(long pageNo,
                                      long pageSize,
                                      String academicYear,
                                      String categoryCode,
                                      String itemCode,
                                      String status,
                                      String keyword,
                                      Long orgUnitId) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.academicYear = academicYear;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.status = status == null || status.isBlank() ? "SUBMITTED" : status;
        this.keyword = keyword;
        this.orgUnitId = orgUnitId;
    }

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getStatus() {
        return status;
    }

    public String getKeyword() {
        return keyword;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public long getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
