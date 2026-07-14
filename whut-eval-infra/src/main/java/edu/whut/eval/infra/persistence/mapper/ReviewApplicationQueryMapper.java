package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.query.ReviewTaskSummaryCounts;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewApplicationQueryMapper {

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildCountReviewApplications")
    long countReviewApplications(@Param("expression") String expression,
                                 @Param("parameters") Map<String, Object> parameters,
                                 @Param("query") ReviewApplicationPageQuery query);

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildSelectReviewApplications")
    List<ReviewApplicationQueryRow> selectReviewApplications(@Param("expression") String expression,
                                                             @Param("parameters") Map<String, Object> parameters,
                                                             @Param("query") ReviewApplicationPageQuery query,
                                                             @Param("offset") long offset,
                                                             @Param("limit") long limit);

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildSelectReviewApplicationDetail")
    ReviewApplicationQueryRow selectReviewApplicationDetail(@Param("expression") String expression,
                                                            @Param("parameters") Map<String, Object> parameters,
                                                            @Param("applicationId") Long applicationId);

    @SelectProvider(type = ReviewApplicationQuerySqlProvider.class, method = "buildCountReviewTaskSummary")
    ReviewTaskSummaryCounts countReviewTaskSummary(@Param("expression") String expression,
                                                   @Param("parameters") Map<String, Object> parameters,
                                                   @Param("reviewerId") Long reviewerId,
                                                   @Param("dayStart") LocalDateTime dayStart,
                                                   @Param("dayEnd") LocalDateTime dayEnd);

    @Select("SELECT file_id AS fileId, storage_key AS storageKey, original_filename AS originalFilename, content_type AS contentType, size, sort_no AS sortNo FROM application_attachment WHERE application_id = #{applicationId} ORDER BY sort_no ASC, id ASC")
    List<ReviewApplicationAttachmentRow> selectAttachments(@Param("applicationId") Long applicationId);
}
