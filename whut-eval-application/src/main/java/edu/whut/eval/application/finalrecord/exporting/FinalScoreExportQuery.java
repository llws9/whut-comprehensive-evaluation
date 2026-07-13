package edu.whut.eval.application.finalrecord.exporting;

import edu.whut.eval.common.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FinalScoreExportQuery(String academicYear,
                                    String status,
                                    String grade,
                                    List<String> classes) {

    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final int MAX_CLASS_TOKEN_COUNT = 500;

    public FinalScoreExportQuery {
        academicYear = normalizeAcademicYear(academicYear);
        status = normalizeStatus(status);
        grade = blankToNull(grade);
        classes = normalizeClasses(classes);
    }

    private static String normalizeAcademicYear(String rawAcademicYear) {
        String normalized = blankToNull(rawAcademicYear);
        if (normalized == null) {
            throw new ValidationException("academicYear 不合法");
        }
        Matcher matcher = ACADEMIC_YEAR_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new ValidationException("academicYear 不合法");
        }
        int startYear = Integer.parseInt(matcher.group(1));
        int endYear = Integer.parseInt(matcher.group(2));
        if (endYear != startYear + 1) {
            throw new ValidationException("academicYear 不合法");
        }
        return normalized;
    }

    private static String normalizeStatus(String rawStatus) {
        String normalized = blankToNull(rawStatus);
        if (normalized == null) {
            return null;
        }
        if (!"SUBMITTED".equals(normalized) && !"CONFIRMED".equals(normalized)) {
            throw new ValidationException("status 仅允许 SUBMITTED 或 CONFIRMED");
        }
        return normalized;
    }

    private static List<String> normalizeClasses(List<String> rawClasses) {
        if (rawClasses == null || rawClasses.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String rawClass : rawClasses) {
            if (rawClass == null) {
                continue;
            }
            String[] split = rawClass.split(",");
            for (String item : split) {
                String token = item.trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        if (tokens.size() > MAX_CLASS_TOKEN_COUNT) {
            throw new ValidationException("classes 参数过多");
        }
        return List.copyOf(new ArrayList<>(tokens));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
