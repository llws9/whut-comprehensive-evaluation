package edu.whut.eval.domain.application.query;

import edu.whut.eval.common.exception.ValidationException;

import java.util.Set;

public class ReviewApplicationPageQuery {

    private static final long MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_STATUSES = Set.of("SUBMITTED", "APPROVED", "RETURNED", "REJECTED");

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
        if (pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("pageSize 不能超过 100");
        }
        String normalizedStatus = status == null || status.isBlank() ? "SUBMITTED" : status.trim();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ValidationException("status 仅允许 SUBMITTED、APPROVED、RETURNED 或 REJECTED");
        }
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.academicYear = academicYear;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.status = normalizedStatus;
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
