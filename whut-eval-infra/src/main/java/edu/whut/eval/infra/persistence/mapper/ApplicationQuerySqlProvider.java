package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ApplicationPageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 正式申请查询 SQL Provider。
 * 它负责把范围 translator 产出的表达式与业务过滤条件安全组合成最终查询语句。
 */
public class ApplicationQuerySqlProvider {

    /**
     * 生成正式申请分页查询的 count SQL。
     */
    public String buildCountAccessibleApplications(Map<String, Object> params) {
        return buildSql("SELECT COUNT(1) FROM application_record", params, false);
    }

    /**
     * 生成正式申请分页查询的列表 SQL。
     */
    public String buildSelectAccessibleApplications(Map<String, Object> params) {
        return buildSql(
                "SELECT application_id AS applicationId, " +
                        "applicant_user_id AS applicantUserId, " +
                        "org_unit_id AS orgUnitId, " +
                        "org_path AS orgPath, " +
                        "category_code AS categoryCode, " +
                        "item_code AS itemCode " +
                        "FROM application_record",
                params,
                true
        );
    }

    /**
     * 授权范围条件和业务过滤条件必须使用 AND 组合，确保业务筛选只会继续收窄可见范围。
     */
    private String buildSql(String selectFromSql, Map<String, Object> params, boolean paged) {
        String expression = params == null ? "" : (String) params.get("expression");
        ApplicationPageQuery query = params == null ? null : (ApplicationPageQuery) params.get("query");
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
            sql.append(" ORDER BY application_id ASC");
            sql.append(" LIMIT #{limit} OFFSET #{offset}");
        }
        return sql.toString();
    }

    /**
     * 这里追加的是业务查询条件，不参与 scope 计算本身。
     */
    private void appendQueryFilters(List<String> conditions, ApplicationPageQuery query) {
        if (query == null) {
            return;
        }
        if (query.getApplicationId() != null) {
            conditions.add("application_id = #{query.applicationId}");
        }
        if (query.getApplicantUserId() != null) {
            conditions.add("applicant_user_id = #{query.applicantUserId}");
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
    }
}
