package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.infra.security.sql.D11ScopeSqlShape;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.apache.ibatis.jdbc.SQL;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinalRecordQuerySqlProvider {

    private static final Pattern SCOPE_PARAMETER_PATTERN =
            Pattern.compile("#\\{scopeFragment\\.parameters\\.([A-Za-z0-9_]+)\\}");

    public String buildCountAdminFinalRecords(Map<String, Object> params) {
        FinalRecordPageQuery query = (FinalRecordPageQuery) params.get("query");
        return baseSelect("COUNT(1)", (String) params.get("expression"), query, false);
    }

    public String buildSelectAdminFinalRecords(Map<String, Object> params) {
        FinalRecordPageQuery query = (FinalRecordPageQuery) params.get("query");
        return baseSelect(selectColumns(), (String) params.get("expression"), query, true)
                + " LIMIT #{limit} OFFSET #{offset}";
    }

    public String buildSelectAdminFinalRecordDetail() {
        return baseSelect(selectColumns(), null, null, false)
                + " AND fr.id = #{recordId}";
    }

    public String buildSelectStudentFinalRecord() {
        return baseSelect(selectColumns(), null, null, false)
                + " AND fr.student_user_id = #{studentUserId}"
                + " AND fr.academic_year = #{academicYear}"
                + " AND fr.status IN ('SUBMITTED', 'CONFIRMED')";
    }

    public String buildCountUnsubmittedStudents(Map<String, Object> params) {
        String scopeExpression = scopeExpression(params, "class_ou");
        String classPredicate = classPredicate(params, "class_ou");
        return """
                SELECT COUNT(1)
                FROM (
                  SELECT u.id AS student_user_id
                  FROM iam_user u
                  JOIN org_membership om
                    ON om.user_id = u.id
                   AND om.membership_type = 'STUDENT'
                   AND om.is_primary = 1
                   AND om.status = 'ACTIVE'
                  JOIN org_unit class_ou
                    ON class_ou.id = om.org_unit_id
                   AND class_ou.unit_type = 'CLASS'
                   AND class_ou.status = 'ACTIVE'
                  LEFT JOIN org_unit grade_ou
                    ON grade_ou.id = class_ou.parent_id
                   AND grade_ou.unit_type = 'GRADE'
                   AND grade_ou.status = 'ACTIVE'
                  WHERE u.status = 'ACTIVE'
                    AND (%s)
                    AND (#{query.grade} IS NULL OR %s OR %s)
                    AND (%s)
                    AND NOT EXISTS (
                      SELECT 1
                      FROM final_record submitted_fr
                      WHERE submitted_fr.student_user_id = u.id
                        AND submitted_fr.academic_year = #{query.academicYear}
                        AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
                    )
                  GROUP BY u.id
                ) visible_students
                """.formatted(scopeExpression,
                caseSensitiveEquals("grade_ou.unit_code", "#{query.grade}"),
                caseSensitiveEquals("grade_ou.unit_name", "#{query.grade}"),
                classPredicate);
    }

    public String buildSelectUnsubmittedStudents(Map<String, Object> params) {
        String scopeExpression = scopeExpression(params, "class_ou1");
        String classPredicate = classPredicate(params, "class_ou1");
        return """
                SELECT
                  u.id AS student_user_id,
                  u.user_no AS user_no,
                  u.user_name AS user_name,
                  grade_ou.unit_name AS grade,
                  class_ou.unit_name AS class_name,
                  draft_fr.last_updated_at AS last_updated_at
                FROM (
                  SELECT visible.user_id, MIN(visible.membership_id) AS membership_id
                  FROM (
                    SELECT om1.user_id, om1.id AS membership_id
                    FROM org_membership om1
                    JOIN iam_user u1
                      ON u1.id = om1.user_id
                     AND u1.status = 'ACTIVE'
                    JOIN org_unit class_ou1
                      ON class_ou1.id = om1.org_unit_id
                     AND class_ou1.unit_type = 'CLASS'
                     AND class_ou1.status = 'ACTIVE'
                    LEFT JOIN org_unit grade_ou1
                      ON grade_ou1.id = class_ou1.parent_id
                     AND grade_ou1.unit_type = 'GRADE'
                     AND grade_ou1.status = 'ACTIVE'
                    WHERE om1.membership_type = 'STUDENT'
                      AND om1.is_primary = 1
                      AND om1.status = 'ACTIVE'
                      AND (%s)
                      AND (#{query.grade} IS NULL OR %s OR %s)
                      AND (%s)
                      AND NOT EXISTS (
                        SELECT 1
                        FROM final_record submitted_fr
                        WHERE submitted_fr.student_user_id = u1.id
                          AND submitted_fr.academic_year = #{query.academicYear}
                          AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
                      )
                  ) visible
                  GROUP BY visible.user_id
                ) picked_visible_om
                JOIN iam_user u
                  ON u.id = picked_visible_om.user_id
                JOIN org_membership om
                  ON om.id = picked_visible_om.membership_id
                JOIN org_unit class_ou
                  ON class_ou.id = om.org_unit_id
                 AND class_ou.unit_type = 'CLASS'
                 AND class_ou.status = 'ACTIVE'
                LEFT JOIN org_unit grade_ou
                  ON grade_ou.id = class_ou.parent_id
                 AND grade_ou.unit_type = 'GRADE'
                 AND grade_ou.status = 'ACTIVE'
                LEFT JOIN (
                  SELECT student_user_id, updated_at AS last_updated_at
                  FROM final_record
                  WHERE academic_year = #{query.academicYear}
                    AND status = 'DRAFT'
                ) draft_fr
                  ON draft_fr.student_user_id = u.id
                ORDER BY CASE WHEN grade_ou.id IS NULL THEN 1 ELSE 0 END ASC,
                         grade_ou.unit_code ASC,
                         class_ou.unit_code ASC,
                         u.user_no ASC,
                         u.id ASC
                LIMIT #{query.pageSize} OFFSET #{query.offset}
                """.formatted(scopeExpression,
                caseSensitiveEquals("grade_ou1.unit_code", "#{query.grade}"),
                caseSensitiveEquals("grade_ou1.unit_name", "#{query.grade}"),
                classPredicate);
    }

    private String baseSelect(String columns, String scopeExpression, FinalRecordPageQuery query, boolean order) {
        SQL sql = new SQL()
                .SELECT(columns)
                .FROM("final_record fr")
                .JOIN("iam_user u ON u.id = fr.student_user_id")
                .LEFT_OUTER_JOIN("org_membership om ON om.user_id = fr.student_user_id AND om.status = 'ACTIVE' AND om.is_primary = 1")
                .LEFT_OUTER_JOIN("org_unit ou ON ou.id = om.org_unit_id")
                .WHERE("fr.status IN ('SUBMITTED', 'CONFIRMED')");
        if (query != null) {
            sql.WHERE("fr.academic_year = #{query.academicYear}");
            if (query.getStatus() != null) {
                sql.WHERE("fr.status = #{query.status}");
            }
            if (query.getOrgUnitId() != null) {
                sql.WHERE("ou.id = #{query.orgUnitId}");
            }
            if (query.getKeyword() != null) {
                sql.WHERE("(u.user_no LIKE CONCAT('%', #{query.keyword}, '%') OR u.user_name LIKE CONCAT('%', #{query.keyword}, '%'))");
            }
        }
        if (scopeExpression != null && !scopeExpression.isBlank()) {
            sql.WHERE(scopeExpression.replace("applicant_user_id", "fr.student_user_id")
                    .replace("org_unit_id", "ou.id")
                    .replace("org_path", "ou.path")
                    .replace("category_code", "fr.status")
                    .replace("item_code", "fr.status"));
        }
        String built = sql.toString();
        if (order) {
            built += " ORDER BY fr.submitted_at DESC, fr.id DESC";
        }
        return built;
    }

    private String selectColumns() {
        return """
                fr.id AS final_record_id,
                fr.student_user_id,
                u.user_no AS student_user_no,
                u.user_name AS student_user_name,
                ou.id AS org_unit_id,
                ou.unit_name AS org_unit_name,
                ou.path AS org_path,
                fr.academic_year,
                fr.status,
                fr.moral_total,
                fr.intellectual_total,
                fr.physical_total,
                fr.labor_total,
                fr.grand_total,
                fr.submitted_at,
                fr.confirmed_at,
                fr.confirm_comment,
                fr.version
                """;
    }

    private String classPredicate(Map<String, Object> params, String classAlias) {
        UnsubmittedFinalRecordQuery query = (UnsubmittedFinalRecordQuery) params.get("query");
        if (query == null || query.isClassesEmpty()) {
            return "TRUE";
        }
        String castClassPlaceholders = castClassPlaceholders(query);
        return "(" + caseSensitiveIn(classAlias + ".unit_code", castClassPlaceholders)
                + " OR " + caseSensitiveIn(classAlias + ".unit_name", castClassPlaceholders) + ")";
    }

    private String castClassPlaceholders(UnsubmittedFinalRecordQuery query) {
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < query.getClasses().size(); i++) {
            placeholders.add("CAST(#{query.classes[" + i + "]} AS BINARY(1024))");
        }
        return String.join(", ", placeholders);
    }

    private String caseSensitiveEquals(String column, String placeholder) {
        return "CAST(" + column + " AS BINARY(1024)) = CAST(" + placeholder + " AS BINARY(1024))";
    }

    private String caseSensitiveIn(String column, String castPlaceholders) {
        return "CAST(" + column + " AS BINARY(1024)) IN (" + castPlaceholders + ")";
    }

    private String scopeExpression(Map<String, Object> params, String classAlias) {
        SqlPredicateFragment fragment = (SqlPredicateFragment) params.get("scopeFragment");
        String expression = fragment == null ? null : fragment.getExpression();
        if (expression == null || expression.isBlank()) {
            return "1 = 0";
        }
        validateD11ScopeExpression(expression, fragment.getParameters());
        return expression.replace(D11ScopeSqlShape.CLASS_ALIAS_PLACEHOLDER, classAlias);
    }

    private void validateD11ScopeExpression(String expression, Map<String, Object> parameters) {
        String normalized = D11ScopeSqlShape.normalize(expression);
        if (!D11ScopeSqlShape.isAllowedScopeExpression(normalized)) {
            throw new IllegalArgumentException("Unsafe D-11 scope expression: shape not whitelisted");
        }
        if (!referencedScopeParameterNames(normalized).equals(parameters.keySet())) {
            throw new IllegalArgumentException("Unsafe D-11 scope expression: parameter names mismatch");
        }
        if (!parameters.values().stream().allMatch(Long.class::isInstance)) {
            throw new IllegalArgumentException("Unsafe D-11 scope expression: non-Long parameter value");
        }
    }

    private Set<String> referencedScopeParameterNames(String normalizedExpression) {
        Matcher matcher = SCOPE_PARAMETER_PATTERN.matcher(normalizedExpression);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
