package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LectureCandidateQueryMapper {

    @Select("""
            SELECT COUNT(1)
            FROM final_component_score fcs
            JOIN final_record fr ON fr.id = fcs.final_record_id
            WHERE fr.student_user_id = #{studentUserId}
              AND fr.academic_year = #{query.academicYear}
              AND fcs.category_code = 'INTELLECTUAL'
              AND fcs.item_code = 'INTELLECTUAL_LECTURE'
              AND fcs.source_type = 'IMPORT'
              AND (#{query.keyword} IS NULL OR fcs.display_text LIKE CONCAT('%', #{query.keyword}, '%'))
            """)
    long countStudentLectureCandidates(@Param("studentUserId") Long studentUserId,
                                        @Param("query") LectureCandidatePageQuery query);

    @Select("""
            SELECT fcs.id AS lectureId,
                   fcs.display_text AS title,
                   fr.academic_year AS academicYear,
                   fcs.score_value AS maxScore,
                   fcs.source_ref_id AS sourceRefId,
                   fcs.created_at AS createdAt
            FROM final_component_score fcs
            JOIN final_record fr ON fr.id = fcs.final_record_id
            WHERE fr.student_user_id = #{studentUserId}
              AND fr.academic_year = #{query.academicYear}
              AND fcs.category_code = 'INTELLECTUAL'
              AND fcs.item_code = 'INTELLECTUAL_LECTURE'
              AND fcs.source_type = 'IMPORT'
              AND (#{query.keyword} IS NULL OR fcs.display_text LIKE CONCAT('%', #{query.keyword}, '%'))
            ORDER BY fcs.created_at ASC, fcs.id ASC
            LIMIT #{query.pageSize} OFFSET #{query.offset}
            """)
    List<LectureCandidateRecord> selectStudentLectureCandidates(@Param("studentUserId") Long studentUserId,
                                                                @Param("query") LectureCandidatePageQuery query);
}
