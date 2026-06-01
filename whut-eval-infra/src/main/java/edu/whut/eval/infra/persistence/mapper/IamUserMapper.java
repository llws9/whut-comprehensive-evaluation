package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
