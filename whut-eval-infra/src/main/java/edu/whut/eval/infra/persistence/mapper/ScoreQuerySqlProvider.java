package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.score.query.ScorePageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 正式成绩查询 SQL Provider。
 * 负责把范围 translator 产出的表达式与业务过滤条件安全组合成最终查询语句。
 */
public class ScoreQuerySqlProvider {

    /**
     * 生成正式成绩分页查询的 count SQL。
     */
    public String buildCountAccessibleScores(Map<String, Object> params) {
        return buildSql("SELECT COUNT(1) FROM score_record", params, false);
    }

    /**
     * 生成正式成绩分页查询的列表 SQL。
     */
    public String buildSelectAccessibleScores(Map<String, Object> params) {
        return buildSql(
                "SELECT score_id AS scoreId, " +
                        "student_user_id AS studentUserId, " +
                        "org_unit_id AS orgUnitId, " +
                        "org_path AS orgPath, " +
                        "category_code AS categoryCode, " +
                        "item_code AS itemCode, " +
                        "academic_year AS academicYear " +
                        "FROM score_record",
                params,
                true
        );
    }

    /**
     * 授权范围条件和业务过滤条件必须使用 AND 组合，确保业务筛选只会继续收窄可见范围。
     *
     * 安全说明：expression 由 ScoreScopeSqlTranslator 生成，已使用参数化查询（#{parameters.pN}），
     * 值通过 parameters Map 传递，不存在 SQL 注入风险。字段名来自硬编码的 fieldMapping，
     * 不是用户直接输入。
     */
    private String buildSql(String selectFromSql, Map<String, Object> params, boolean paged) {
        String expression = params == null ? "" : (String) params.get("expression");
        ScorePageQuery query = params == null ? null : (ScorePageQuery) params.get("query");
        List<String> conditions = new ArrayList<>();
        if (expression != null && !expression.isBlank()) {
            conditions.add("(" + expression + ")");
        }
        appendQueryFilters(conditions, query);

        StringBuilder sql = new StringBuilder(selectFromSql);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (paged) {
            sql.append(" ORDER BY score_id ASC");
            sql.append(" LIMIT #{limit} OFFSET #{offset}");
        }
        return sql.toString();
    }

    /**
     * 这里追加的是业务查询条件，不参与 scope 计算本身。
     */
    private void appendQueryFilters(List<String> conditions, ScorePageQuery query) {
        if (query == null) {
            return;
        }
        if (query.getScoreId() != null) {
            conditions.add("score_id = #{query.scoreId}");
        }
        if (query.getStudentUserId() != null) {
            conditions.add("student_user_id = #{query.studentUserId}");
        }
        if (query.getOrgUnitId() != null) {
            conditions.add("org_unit_id = #{query.orgUnitId}");
        }
        if (query.getCategoryCode() != null && !query.getCategoryCode().isBlank()) {
            conditions.add("category_code = #{query.categoryCode}");
        }
        if (query.getItemCode() != null && !query.getItemCode().isBlank()) {
            conditions.add("item_code = #{query.itemCode}");
        }
        if (query.getAcademicYear() != null && !query.getAcademicYear().isBlank()) {
            conditions.add("academic_year = #{query.academicYear}");
        }
    }
}
