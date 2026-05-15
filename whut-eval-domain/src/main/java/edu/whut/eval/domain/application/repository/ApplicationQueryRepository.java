package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;

/**
 * 正式申请查询仓储接口。
 * application service 应通过该接口获取“当前用户在某权限下可见的申请列表”，而不是直接接触 Mapper。
 */
public interface ApplicationQueryRepository {

    /**
     * 按访问上下文与业务过滤条件分页查询可访问申请。
     */
    PageResult<ApplicationRecord> pageAccessibleApplications(ApplicationAccessContext accessContext,
                                                             ApplicationPageQuery query);
}
