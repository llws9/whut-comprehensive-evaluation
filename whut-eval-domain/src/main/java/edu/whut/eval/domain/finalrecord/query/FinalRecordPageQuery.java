package edu.whut.eval.domain.finalrecord.query;

import edu.whut.eval.common.exception.ValidationException;

import java.util.Set;

public class FinalRecordPageQuery {

    private static final Set<String> ALLOWED_STATUSES = Set.of("SUBMITTED", "CONFIRMED");
    private final String academicYear;
    private final String status;
    private final String keyword;
    private final Long orgUnitId;
    private final long pageNo;
    private final long pageSize;

    public FinalRecordPageQuery(String academicYear, String status, String keyword, Long orgUnitId, long pageNo, long pageSize) {
        if (academicYear == null || academicYear.isBlank()) {
            throw new ValidationException("academicYear 不能为空");
        }
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        if (normalizedStatus != null && !ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ValidationException("status 仅允许 SUBMITTED 或 CONFIRMED");
        }
        this.academicYear = academicYear.trim();
        this.status = normalizedStatus;
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        this.orgUnitId = orgUnitId;
        this.pageNo = pageNo <= 0 ? 1 : pageNo;
        this.pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
    }

    public String getAcademicYear() { return academicYear; }
    public String getStatus() { return status; }
    public String getKeyword() { return keyword; }
    public Long getOrgUnitId() { return orgUnitId; }
    public long getPageNo() { return pageNo; }
    public long getPageSize() { return pageSize; }
    public long getOffset() { return (pageNo - 1) * pageSize; }
}
