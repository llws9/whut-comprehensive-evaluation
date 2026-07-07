package edu.whut.eval.application.application.repository;

import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;

import java.util.Optional;

public interface ReviewApplicationQueryRepository {

    PageResult<ReviewApplicationQueryRow> pageReviewApplications(ApplicationAccessContext accessContext,
                                                                 ReviewApplicationPageQuery query);

    Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(Long applicationId);

    Optional<ReviewApplicationQueryRow> findReviewApplicationDetail(ApplicationAccessContext accessContext,
                                                                    Long applicationId);
}
