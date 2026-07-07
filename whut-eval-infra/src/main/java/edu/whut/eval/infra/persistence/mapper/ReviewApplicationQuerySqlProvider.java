package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReviewApplicationQuerySqlProvider {

    private static final String REVIEW_BASE = """
            FROM (
                SELECT s.application_id AS application_id,
                       s.applicant_user_id AS applicant_user_id,
                       u.user_name AS user_name,
                       u.user_no AS user_no,
                       s.org_unit_id AS org_unit_id,
                       o.unit_name AS org_unit_name,
                       o.path AS org_path,
                       s.category_code AS category_code,
                       s.item_code AS item_code,
                       s.academic_year AS academic_year,
                       s.term AS term,
                       s.title AS title,
                       s.description AS description,
                       s.status AS status,
                       s.submitted_at AS submitted_at,
                       s.version AS version,
                       f.score_value AS score_value,
                       f.evidence_count AS evidence_count,
                       f.extra_json AS extra_json,
                       f.display_text AS display_text
                FROM application_submission s
                JOIN iam_user u ON u.id = s.applicant_user_id
                JOIN org_unit o ON o.id = s.org_unit_id
                LEFT JOIN application_fact f ON f.application_id = s.application_id
            ) review_base
            """;

    public String buildCountReviewApplications(Map<String, Object> params) {
        return buildSql("SELECT COUNT(1) " + REVIEW_BASE, params, false, false);
    }

    public String buildSelectReviewApplications(Map<String, Object> params) {
        return buildSql(selectColumns() + REVIEW_BASE, params, true, false);
    }

    public String buildSelectReviewApplicationDetail(Map<String, Object> params) {
        return buildSql(selectColumns() + REVIEW_BASE, params, false, true);
    }

    private String buildSql(String selectFromSql, Map<String, Object> params, boolean paged, boolean detail) {
        String expression = params == null ? "" : (String) params.get("expression");
        ReviewApplicationPageQuery query = params == null || !params.containsKey("query")
                ? null
                : (ReviewApplicationPageQuery) params.get("query");
        List<String> conditions = new ArrayList<>();
        if (expression != null && !expression.isBlank()) {
            conditions.add("(" + expression + ")");
        }
        if (detail) {
            conditions.add("application_id = #{applicationId}");
        } else {
            appendQueryFilters(conditions, query);
        }
        StringBuilder sql = new StringBuilder(selectFromSql);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (paged) {
            sql.append(" ORDER BY submitted_at ASC, application_id ASC");
            sql.append(" LIMIT #{limit} OFFSET #{offset}");
        }
        return sql.toString();
    }

    private String selectColumns() {
        return """
                SELECT application_id AS applicationId,
                       applicant_user_id AS applicantUserId,
                       user_name AS applicantUserName,
                       user_no AS applicantUserNo,
                       org_unit_id AS orgUnitId,
                       org_unit_name AS orgUnitName,
                       org_path AS orgPath,
                       category_code AS categoryCode,
                       item_code AS itemCode,
                       academic_year AS academicYear,
                       term AS term,
                       title AS title,
                       description AS description,
                       status AS status,
                       submitted_at AS submittedAt,
                       version AS version,
                       score_value AS appliedPoints,
                       evidence_count AS evidenceCount,
                       extra_json AS extraJson,
                       display_text AS warningMessage
                """;
    }

    private void appendQueryFilters(List<String> conditions, ReviewApplicationPageQuery query) {
        if (query == null) {
            conditions.add("status = 'SUBMITTED'");
            return;
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            conditions.add("status = #{query.status}");
        }
        if (query.getAcademicYear() != null && !query.getAcademicYear().isBlank()) {
            conditions.add("academic_year = #{query.academicYear}");
        }
        if (query.getCategoryCode() != null && !query.getCategoryCode().isBlank()) {
            conditions.add("category_code = #{query.categoryCode}");
        }
        if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
            conditions.add("item_code = #{query.itemCode}");
        }
        if (query.getOrgUnitId() != null) {
            conditions.add("org_unit_id = #{query.orgUnitId}");
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            conditions.add("(user_name LIKE CONCAT('%', #{query.keyword}, '%') OR user_no LIKE CONCAT('%', #{query.keyword}, '%'))");
        }
    }
}
