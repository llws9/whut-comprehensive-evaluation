package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.OrgUnitDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgUnitMapper extends BaseMapper<OrgUnitDO> {
}
