package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.infra.persistence.query.ScoreQueryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * 正式成绩列表查询 Mapper。
 */
@Mapper
public interface ScoreQueryMapper {

    /**
     * 统计当前用户在目标权限和业务过滤条件下可见的成绩总数。
     */
    @SelectProvider(type = ScoreQuerySqlProvider.class, method = "buildCountAccessibleScores")
    long countAccessibleScores(@Param("expression") String expression,
                               @Param("parameters") Map<String, Object> parameters,
                               @Param("query") ScorePageQuery query);

    /**
     * 分页查询当前用户在目标权限和业务过滤条件下可见的成绩列表。
     */
    @SelectProvider(type = ScoreQuerySqlProvider.class, method = "buildSelectAccessibleScores")
    List<ScoreQueryRow> selectAccessibleScores(@Param("expression") String expression,
                                               @Param("parameters") Map<String, Object> parameters,
                                               @Param("query") ScorePageQuery query,
                                               @Param("offset") long offset,
                                               @Param("limit") long limit);
}
