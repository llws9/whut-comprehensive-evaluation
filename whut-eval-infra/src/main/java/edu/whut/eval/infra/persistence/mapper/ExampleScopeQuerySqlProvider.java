package edu.whut.eval.infra.persistence.mapper;

import java.util.Map;

/**
 * 统一为示例申请/成绩查询生成最终 SQL。
 * 这里故意直接消费 translator 产出的表达式片段，演示 Repository/Mapper 的真实接线方式。
 */
public class ExampleScopeQuerySqlProvider {

    /**
     * 生成示例申请列表查询 SQL。
     */
    public String buildSelectAccessibleApplications(Map<String, Object> params) {
        return buildSelectSql(
                "SELECT application_id AS applicationId, " +
                        "applicant_user_id AS applicantUserId, " +
                        "org_unit_id AS orgUnitId, " +
                        "org_path AS orgPath, " +
                        "category_code AS categoryCode, " +
                        "item_code AS itemCode " +
                        "FROM example_application_record",
                params
        );
    }

    /**
     * 生成示例成绩列表查询 SQL。
     */
    public String buildSelectAccessibleScores(Map<String, Object> params) {
        return buildSelectSql(
                "SELECT score_id AS scoreId, " +
                        "student_user_id AS studentUserId, " +
                        "org_unit_id AS orgUnitId, " +
                        "org_path AS orgPath, " +
                        "category_code AS categoryCode, " +
                        "item_code AS itemCode, " +
                        "academic_year AS academicYear " +
                        "FROM example_score_record",
                params
        );
    }

    /**
     * translator 负责产出安全的参数化表达式，这里只负责把它接到最终 SELECT 语句上。
     */
    private String buildSelectSql(String selectFromSql, Map<String, Object> params) {
        String expression = params == null ? "" : (String) params.get("expression");
        StringBuilder sql = new StringBuilder(selectFromSql);
        if (expression != null && !expression.isBlank()) {
            sql.append(" WHERE ").append(expression);
        }
        sql.append(" ORDER BY 1 ASC");
        return sql.toString();
    }
}
