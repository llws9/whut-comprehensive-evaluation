package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.repository.row.PermissionDictionaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminPermissionDictionaryMapper {

    @Select({
            "<script>",
            "SELECT",
            "  permission_code AS permissionCode,",
            "  permission_name AS permissionName,",
            "  permission_group AS module,",
            "  NULL AS description,",
            "  status AS status",
            "FROM iam_permission",
            "WHERE 1 = 1",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (permission_code LIKE CONCAT('%', #{keyword}, '%')",
            "       OR permission_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='module != null and module != \"\"'>",
            "  AND permission_group = #{module}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND status = #{status}",
            "</if>",
            "ORDER BY id ASC",
            "</script>"
    })
    List<PermissionDictionaryRow> selectPermissions(@Param("keyword") String keyword,
                                                    @Param("module") String module,
                                                    @Param("status") String status);
}
