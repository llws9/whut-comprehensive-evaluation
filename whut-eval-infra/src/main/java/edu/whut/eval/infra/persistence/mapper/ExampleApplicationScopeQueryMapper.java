package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.query.ExampleApplicationQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * 示例申请 Mapper：展示如何在真正的 Mapper 层消费 translator 生成的 SQL 片段。
 */
@Mapper
public interface ExampleApplicationScopeQueryMapper {

    /**
     * 查询当前范围谓词可见的申请资源列表。
     */
    @SelectProvider(type = ExampleScopeQuerySqlProvider.class, method = "buildSelectAccessibleApplications")
    List<ExampleApplicationQueryRow> selectAccessibleApplications(@Param("expression") String expression,
                                                                 @Param("parameters") Map<String, Object> parameters);
}
