package edu.whut.eval.domain.finalrecord.query;

import edu.whut.eval.common.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class UnsubmittedFinalRecordQuery {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("^\\d{4}-\\d{4}$");
    private static final int MAX_CLASSES = 500;
    private static final int MAX_FILTER_VALUE_LENGTH = 256;

    private final String academicYear;
    private final String grade;
    private final List<String> classes;
    private final long pageNo;
    private final long pageSize;
    private final long offset;

    public UnsubmittedFinalRecordQuery(String academicYear, String grade, List<String> classes, long pageNo, long pageSize) {
        this.academicYear = normalizeAcademicYear(academicYear);
        this.grade = normalizeFilterValue(grade, "grade");
        this.classes = normalizeClasses(classes);
        this.pageNo = pageNo <= 0 ? 1 : pageNo;
        this.pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        this.offset = checkedOffset(this.pageNo, this.pageSize);
    }

    private String normalizeAcademicYear(String value) {
        if (value == null) {
            throw new ValidationException("academicYear 不合法");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || !ACADEMIC_YEAR.matcher(trimmed).matches()) {
            throw new ValidationException("academicYear 不合法");
        }
        try {
            int start = Integer.parseInt(trimmed.substring(0, 4));
            int end = Integer.parseInt(trimmed.substring(5, 9));
            if (end != start + 1) {
                throw new ValidationException("academicYear 不合法");
            }
        } catch (NumberFormatException ex) {
            throw new ValidationException("academicYear 不合法");
        }
        return trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    private String normalizeFilterValue(String value, String name) {
        String trimmed = normalizeOptional(value);
        if (trimmed != null && trimmed.length() > MAX_FILTER_VALUE_LENGTH) {
            throw new ValidationException(name + " 不合法");
        }
        return trimmed;
    }

    private List<String> normalizeClasses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = normalizeFilterValue(value, "classes");
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        if (normalized.size() > MAX_CLASSES) {
            throw new ValidationException("classes 不合法");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private long checkedOffset(long pageNo, long pageSize) {
        try {
            return Math.multiplyExact(Math.subtractExact(pageNo, 1), pageSize);
        } catch (ArithmeticException ex) {
            throw new ValidationException("pageNo 不合法");
        }
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public String getGrade() {
        return grade;
    }

    public List<String> getClasses() {
        return classes;
    }

    public boolean isClassesEmpty() {
        return classes.isEmpty();
    }

    public long getPageNo() {
        return pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public long getOffset() {
        return offset;
    }
}
