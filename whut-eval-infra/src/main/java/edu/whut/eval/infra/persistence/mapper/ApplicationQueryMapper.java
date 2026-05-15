package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.infra.persistence.query.ApplicationQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * 正式申请列表查询 Mapper。
 */
@Mapper
public interface ApplicationQueryMapper {

    /**
     * 统计当前用户在目标权限和业务过滤条件下可见的申请总数。
     */
    @SelectProvider(type = ApplicationQuerySqlProvider.class, method = "buildCountAccessibleApplications")
    long countAccessibleApplications(@Param("expression") String expression,
                                     @Param("parameters") Map<String, Object> parameters,
                                     @Param("query") ApplicationPageQuery query);

    /**
     * 分页查询当前用户在目标权限和业务过滤条件下可见的申请列表。
     */
    @SelectProvider(type = ApplicationQuerySqlProvider.class, method = "buildSelectAccessibleApplications")
    List<ApplicationQueryRow> selectAccessibleApplications(@Param("expression") String expression,
                                                           @Param("parameters") Map<String, Object> parameters,
                                                           @Param("query") ApplicationPageQuery query,
                                                           @Param("offset") long offset,
                                                           @Param("limit") long limit);
}
