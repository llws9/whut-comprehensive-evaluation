package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IamPermissionLookupMapper {

    @Select({
            "<script>",
            "SELECT id AS permissionId, permission_code AS permissionCode FROM iam_permission",
            "WHERE permission_code IN",
            "<foreach collection='permissionCodes' item='code' open='(' separator=',' close=')'>",
            "#{code}",
            "</foreach>",
            "</script>"
    })
    List<PermissionIdCodeRow> selectIdsByCodes(@Param("permissionCodes") List<String> permissionCodes);
}
