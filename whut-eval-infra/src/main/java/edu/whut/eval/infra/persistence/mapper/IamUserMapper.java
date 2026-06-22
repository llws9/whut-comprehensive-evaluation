package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IamUserMapper extends BaseMapper<IamUserDO> {

    @Select("SELECT id FROM iam_user WHERE id = #{userId} FOR UPDATE")
    Long selectIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE iam_user SET status = #{status}, updated_at = NOW() WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") String status);

    @Update("""
            UPDATE iam_user
            SET user_name = #{userName},
                password_hash = #{passwordHash},
                email = #{email},
                phone = #{phone},
                updated_at = NOW()
            WHERE user_no = #{userNo}
            """)
    int updateForImportByUserNo(@Param("userNo") String userNo,
                                @Param("userName") String userName,
                                @Param("passwordHash") String passwordHash,
                                @Param("email") String email,
                                @Param("phone") String phone);

    @Select({
            "<script>",
            "SELECT om.user_id AS userId, ou.unit_name AS orgUnitName",
            "FROM org_membership om",
            "JOIN org_unit ou ON ou.id = om.org_unit_id",
            "WHERE om.status = 'ACTIVE' AND om.user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "ORDER BY om.is_primary DESC, om.id ASC",
            "</script>"
    })
    List<UserOrgUnitNameRow> selectActiveOrgUnitNamesByUserIds(@Param("userIds") List<Long> userIds);

    @Select({
            "<script>",
            "SELECT ura.user_id AS userId, r.role_code AS roleCode",
            "FROM iam_user_role_assignment ura",
            "JOIN iam_role r ON r.id = ura.role_id",
            "WHERE ura.status = 'ACTIVE'",
            "AND ura.effective_from &lt;= NOW()",
            "AND (ura.effective_to IS NULL OR ura.effective_to > NOW())",
            "AND ura.user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "ORDER BY ura.id ASC",
            "</script>"
    })
    List<UserRoleCodeRow> selectActiveRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    record UserOrgUnitNameRow(Long userId, String orgUnitName) {
    }

    record UserRoleCodeRow(Long userId, String roleCode) {
    }
}
