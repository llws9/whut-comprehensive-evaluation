package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import org.apache.ibatis.jdbc.SQL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinalRecordQuerySqlProvider {

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

    public String buildSelectAdminFinalScoreExportRows(Map<String, Object> params) {
        FinalScoreExportQuery query = (FinalScoreExportQuery) params.get("query");
        SQL sql = new SQL()
                .SELECT(exportColumns())
                .FROM("final_record fr")
                .JOIN("iam_user u ON u.id = fr.student_user_id")
                .LEFT_OUTER_JOIN("""
                        org_membership om ON om.user_id = fr.student_user_id
                        AND om.status = 'ACTIVE'
                        AND om.is_primary = 1
                        AND om.id = (
                            SELECT MIN(om2.id)
                            FROM org_membership om2
                            WHERE om2.user_id = fr.student_user_id
                              AND om2.status = 'ACTIVE'
                              AND om2.is_primary = 1
                        )
                        """)
                .LEFT_OUTER_JOIN("""
                        org_unit class_ou ON class_ou.id = om.org_unit_id
                        AND class_ou.unit_type = 'CLASS'
                        AND class_ou.status = 'ACTIVE'
                        """)
                .LEFT_OUTER_JOIN("""
                        org_unit grade_ou ON grade_ou.id = class_ou.parent_id
                        AND grade_ou.unit_type = 'GRADE'
                        AND grade_ou.status = 'ACTIVE'
                        """)
                .WHERE("fr.academic_year = #{query.academicYear}");
        if (query.status() == null) {
            sql.WHERE("fr.status IN ('SUBMITTED', 'CONFIRMED')");
        } else {
            sql.WHERE("fr.status = #{query.status}");
        }
        appendExportScope(sql, (String) params.get("expression"));
        if (query.grade() != null) {
            sql.WHERE("("
                    + caseSensitiveEquals("grade_ou.unit_code", "#{query.grade}")
                    + " OR "
                    + caseSensitiveEquals("grade_ou.unit_name", "#{query.grade}")
                    + ")");
        }
        if (!query.classes().isEmpty()) {
            String collectionExpression = classTokenPlaceholders(query.classes().size());
            sql.WHERE("("
                    + caseSensitiveIn("class_ou.unit_code", collectionExpression)
                    + " OR "
                    + caseSensitiveIn("class_ou.unit_name", collectionExpression)
                    + ")");
        }
        return sql
                + """
                 ORDER BY CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END,
                          grade_ou.unit_code ASC,
                          CASE WHEN class_ou.unit_code IS NULL THEN 1 ELSE 0 END,
                          class_ou.unit_code ASC,
                          u.user_no ASC,
                          fr.id ASC
                 LIMIT #{limit}
                """;
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

    private String exportColumns() {
        return """
                fr.id AS final_record_id,
                fr.student_user_id,
                u.user_no AS student_user_no,
                u.user_name AS student_user_name,
                grade_ou.unit_code AS grade_code,
                grade_ou.unit_name AS grade_name,
                class_ou.unit_code AS class_code,
                class_ou.unit_name AS class_name,
                fr.academic_year,
                fr.status,
                fr.moral_total,
                fr.intellectual_total,
                fr.physical_total,
                fr.labor_total,
                fr.grand_total,
                fr.submitted_at,
                fr.confirmed_at
                """;
    }

    private void appendExportScope(SQL sql, String scopeExpression) {
        if (scopeExpression != null && !scopeExpression.isBlank()) {
            sql.WHERE(scopeExpression.replace("applicant_user_id", "fr.student_user_id")
                    .replace("org_unit_id", "class_ou.id")
                    .replace("org_path", "class_ou.path"));
        }
    }

    private String caseSensitiveEquals(String column, String parameter) {
        return "CAST(" + column + " AS BINARY(1024)) = CAST(" + parameter + " AS BINARY(1024))";
    }

    private String caseSensitiveIn(String column, String collectionExpression) {
        return "CAST(" + column + " AS BINARY(1024)) IN (" + collectionExpression + ")";
    }

    private String classTokenPlaceholders(int size) {
        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            placeholders.add("CAST(#{query.classes[" + index + "]} AS BINARY(1024))");
        }
        return String.join(", ", placeholders);
    }
}
