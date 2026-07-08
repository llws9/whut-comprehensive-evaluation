package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.query.ApprovedApplicationFactRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinalRecordAggregationMapper {

    @Select("""
            SELECT application_id
            FROM application_submission
            WHERE applicant_user_id = #{studentUserId}
              AND academic_year = #{academicYear}
              AND status = 'APPROVED'
            ORDER BY category_code ASC, item_code ASC, application_id ASC
            """)
    List<Long> selectApprovedApplicationIds(@Param("studentUserId") long studentUserId,
                                            @Param("academicYear") String academicYear);

    @Select("""
            <script>
            SELECT s.application_id,
                   s.category_code,
                   s.item_code,
                   f.score_value,
                   f.display_text,
                   'APPLICATION' AS source_type,
                   CAST(s.application_id AS CHAR) AS source_ref_id
            FROM application_submission s
            JOIN application_fact f ON f.application_id = s.application_id
            WHERE s.application_id IN
            <foreach collection="applicationIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            ORDER BY s.category_code ASC, s.item_code ASC, s.application_id ASC
            </script>
            """)
    List<ApprovedApplicationFactRow> selectApprovedFacts(@Param("applicationIds") List<Long> applicationIds);
}
