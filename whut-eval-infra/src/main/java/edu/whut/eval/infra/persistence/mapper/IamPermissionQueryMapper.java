package edu.whut.eval.infra.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IamPermissionQueryMapper {

    @Select("SELECT DISTINCT p.permission_code " +
            "FROM iam_user_role_assignment a " +
            "INNER JOIN iam_role r ON r.id = a.role_id " +
            "INNER JOIN iam_role_permission rp ON rp.role_id = a.role_id " +
            "INNER JOIN iam_permission p ON p.id = rp.permission_id " +
            "WHERE a.user_id = #{userId} " +
            "  AND a.status = 'ACTIVE' " +
            "  AND r.status = 'ACTIVE' " +
            "  AND p.status = 'ACTIVE' " +
            "  AND a.effective_from <= NOW() " +
            "  AND (a.effective_to IS NULL OR a.effective_to > NOW())")
    List<String> selectActivePermissionCodesByUserId(@Param("userId") Long userId);
}
