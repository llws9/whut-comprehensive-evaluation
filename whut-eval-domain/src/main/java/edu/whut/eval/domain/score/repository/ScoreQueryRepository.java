package edu.whut.eval.domain.score.repository;

import edu.whut.eval.domain.score.model.ScoreRecord;
import edu.whut.eval.domain.score.query.ScoreAccessContext;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.shared.PageResult;

/**
 * 正式成绩查询仓储接口。
 */
public interface ScoreQueryRepository {

    /**
     * 按访问上下文与业务过滤条件分页查询可访问成绩。
     */
    PageResult<ScoreRecord> pageAccessibleScores(ScoreAccessContext accessContext,
                                                 ScorePageQuery query);
}
