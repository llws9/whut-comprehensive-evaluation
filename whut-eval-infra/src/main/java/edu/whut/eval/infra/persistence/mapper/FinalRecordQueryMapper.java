package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface FinalRecordQueryMapper {

    @SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildCountAdminFinalRecords")
    long countAdminFinalRecords(@Param("expression") String expression,
                                @Param("parameters") Map<String, Object> parameters,
                                @Param("query") FinalRecordPageQuery query);

    @SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectAdminFinalRecords")
    List<FinalRecordQueryRow> selectAdminFinalRecords(@Param("expression") String expression,
                                                      @Param("parameters") Map<String, Object> parameters,
                                                      @Param("query") FinalRecordPageQuery query,
                                                      @Param("offset") long offset,
                                                      @Param("limit") long limit);

    @SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectAdminFinalScoreExportRows")
    List<FinalScoreExportRow> selectAdminFinalScoreExportRows(@Param("expression") String expression,
                                                              @Param("parameters") Map<String, Object> parameters,
                                                              @Param("query") FinalScoreExportQuery query,
                                                              @Param("limit") int limit);

    @SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectAdminFinalRecordDetail")
    FinalRecordQueryRow selectAdminFinalRecordDetail(@Param("recordId") Long recordId);

    @SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectStudentFinalRecord")
    FinalRecordQueryRow selectStudentFinalRecord(@Param("studentUserId") Long studentUserId,
                                                 @Param("academicYear") String academicYear);

    @Select("""
            SELECT id,
                   final_record_id,
                   category_code,
                   item_code,
                   NULL AS item_name,
                   score_value,
                   display_text,
                   source_type,
                   source_ref_id,
                   created_at
            FROM final_component_score
            WHERE final_record_id = #{finalRecordId}
            ORDER BY category_code ASC, item_code ASC, id ASC
            """)
    List<FinalComponentScoreRow> selectComponents(@Param("finalRecordId") Long finalRecordId);
}
