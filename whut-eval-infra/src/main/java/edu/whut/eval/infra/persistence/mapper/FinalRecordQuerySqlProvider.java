package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import org.apache.ibatis.jdbc.SQL;

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
}
