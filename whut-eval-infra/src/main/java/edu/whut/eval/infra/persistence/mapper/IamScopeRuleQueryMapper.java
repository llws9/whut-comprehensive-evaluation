package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.query.IamScopeRuleRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IamScopeRuleQueryMapper {

    @Select("SELECT sr.assignment_id AS assignmentId, " +
            "sr.permission_code AS permissionCode, " +
            "sr.scope_type AS scopeType, " +
            "sr.org_unit_id AS orgUnitId, " +
            "sr.category_code AS categoryCode, " +
            "sr.item_code AS itemCode, " +
            "sr.expression_json AS expressionJson, " +
            "sr.priority AS priority, " +
            "sr.status AS status " +
            "FROM iam_scope_rule sr " +
            "INNER JOIN iam_user_role_assignment a ON a.id = sr.assignment_id " +
            "INNER JOIN iam_role r ON r.id = a.role_id " +
            "INNER JOIN iam_permission p ON p.permission_code = sr.permission_code " +
            "INNER JOIN iam_role_permission rp ON rp.role_id = a.role_id AND rp.permission_id = p.id " +
            "WHERE a.user_id = #{userId} " +
            "  AND a.status = 'ACTIVE' " +
            "  AND sr.status = 'ACTIVE' " +
            "  AND r.status = 'ACTIVE' " +
            "  AND p.status = 'ACTIVE' " +
            "  AND a.effective_from <= NOW() " +
            "  AND (a.effective_to IS NULL OR a.effective_to > NOW()) " +
            "ORDER BY sr.priority ASC, sr.permission_code ASC, sr.id ASC")
    List<IamScopeRuleRow> selectActiveScopeRulesByUserId(@Param("userId") Long userId);
}
