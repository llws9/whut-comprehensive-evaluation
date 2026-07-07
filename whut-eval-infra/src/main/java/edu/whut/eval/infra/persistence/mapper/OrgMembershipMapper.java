package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrgMembershipMapper extends BaseMapper<OrgMembershipDO> {

    @Select("SELECT COUNT(1) FROM org_membership WHERE user_id = #{userId} AND org_unit_id = #{orgUnitId} AND status = 'ACTIVE'")
    int countActiveByUserIdAndOrgUnitId(@Param("userId") Long userId,
                                        @Param("orgUnitId") Long orgUnitId);
}
