package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IamUserMapper extends BaseMapper<IamUserDO> {

    @Select("SELECT id FROM iam_user WHERE id = #{userId} FOR UPDATE")
    Long selectIdForUpdate(@Param("userId") Long userId);
}
