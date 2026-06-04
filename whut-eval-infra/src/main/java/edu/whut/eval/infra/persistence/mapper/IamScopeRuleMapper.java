package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.IamScopeRuleDO;
import edu.whut.eval.infra.persistence.repository.row.IamScopeRuleAdminRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IamScopeRuleMapper extends BaseMapper<IamScopeRuleDO> {

    @Select("SELECT sr.id AS scopeRuleId, " +
            "sr.assignment_id AS assignmentId, " +
            "sr.permission_code AS permissionCode, " +
            "sr.scope_type AS scopeType, " +
            "sr.org_unit_id AS orgUnitId, " +
            "ou.unit_name AS orgUnitName, " +
            "sr.category_code AS categoryCode, " +
            "sr.item_code AS itemCode, " +
            "sr.expression_json AS expressionJson, " +
            "sr.priority AS priority, " +
            "sr.status AS status, " +
            "sr.created_at AS createdAt " +
            "FROM iam_scope_rule sr " +
            "LEFT JOIN org_unit ou ON ou.id = sr.org_unit_id " +
            "WHERE sr.assignment_id = #{assignmentId} " +
            "ORDER BY sr.priority ASC, sr.id ASC")
    List<IamScopeRuleAdminRow> selectAdminRowsByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Select("SELECT COUNT(1) " +
            "FROM iam_user_role_assignment a " +
            "INNER JOIN iam_role_permission rp ON rp.role_id = a.role_id " +
            "INNER JOIN iam_permission p ON p.id = rp.permission_id " +
            "WHERE a.id = #{assignmentId} " +
            "  AND p.permission_code = #{permissionCode} " +
            "  AND p.status = 'ACTIVE'")
    long countAssignmentRolePermission(@Param("assignmentId") Long assignmentId,
                                       @Param("permissionCode") String permissionCode);
}
