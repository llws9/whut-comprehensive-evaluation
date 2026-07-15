package edu.whut.eval.domain.application.query;

import edu.whut.eval.common.exception.ValidationException;

public record LectureCandidatePageQuery(String academicYear, String keyword, long pageNo, long pageSize) {

    private static final long MAX_PAGE_SIZE = 100;

    public LectureCandidatePageQuery {
        if (academicYear == null || academicYear.isBlank()) {
            throw new ValidationException("academicYear 不能为空");
        }
        academicYear = academicYear.trim();
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (pageNo < 1) {
            throw new ValidationException("pageNo 必须大于 0");
        }
        if (pageSize < 1) {
            throw new ValidationException("pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("pageSize 不能超过 100");
        }
    }

    public long offset() {
        return (pageNo - 1) * pageSize;
    }
}
