package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.OrgMembershipDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgMembershipMapper extends BaseMapper<OrgMembershipDO> {
}
