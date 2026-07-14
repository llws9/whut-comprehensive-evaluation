package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreCategoryTotalRow;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreImportStudentTargetRow;
import edu.whut.eval.infra.persistence.repository.row.MentorScoreImportedComponentRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MentorScoreImportMapper {

    @Select("""
            SELECT u.id AS student_user_id,
                   u.user_no AS student_no,
                   ou.id AS org_unit_id,
                   ou.path AS org_path,
                   fr.status AS final_record_status
            FROM iam_user u
            JOIN org_membership om ON om.user_id = u.id AND om.status = 'ACTIVE' AND om.is_primary = 1
            JOIN org_unit ou ON ou.id = om.org_unit_id AND ou.status = 'ACTIVE'
            LEFT JOIN final_record fr ON fr.student_user_id = u.id AND fr.academic_year = #{academicYear}
            WHERE u.user_no = #{studentNo}
              AND u.status = 'ACTIVE'
            LIMIT 1
            """)
    MentorScoreImportStudentTargetRow selectTarget(@Param("studentNo") String studentNo,
                                                   @Param("academicYear") String academicYear);

    @Select("SELECT path FROM org_unit WHERE id = #{orgUnitId} AND status = 'ACTIVE'")
    String selectActiveOrgPath(@Param("orgUnitId") Long orgUnitId);

    @Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE student_user_id = #{studentUserId} AND academic_year = #{academicYear} FOR UPDATE")
    FinalRecordDO selectFinalRecordForUpdate(@Param("studentUserId") Long studentUserId,
                                             @Param("academicYear") String academicYear);

    @Insert("INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at) VALUES (#{studentUserId}, #{academicYear}, #{status}, #{moralTotal}, #{intellectualTotal}, #{physicalTotal}, #{laborTotal}, #{grandTotal}, #{submittedAt}, #{confirmedAt}, #{confirmComment}, #{version}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDraft(FinalRecordDO record);

    @Select("SELECT id, final_record_id, category_code, item_code, score_value, display_text, source_ref_id, created_at FROM final_component_score WHERE final_record_id = #{finalRecordId} AND category_code = #{categoryCode} AND item_code = #{itemCode} AND source_type = 'IMPORT' LIMIT 1")
    MentorScoreImportedComponentRow selectImportedComponent(@Param("finalRecordId") Long finalRecordId,
                                                            @Param("categoryCode") String categoryCode,
                                                            @Param("itemCode") String itemCode);

    @Insert("INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at) VALUES (#{finalRecordId}, #{categoryCode}, #{itemCode}, #{scoreValue}, #{displayText}, 'IMPORT', #{sourceRefId}, #{createdAt})")
    int insertImportedComponent(MentorScoreImportedComponentRow component);

    @Update("UPDATE final_component_score SET score_value = #{scoreValue}, display_text = #{displayText}, source_ref_id = #{sourceRefId}, created_at = #{createdAt} WHERE id = #{id} AND source_type = 'IMPORT'")
    int updateImportedComponent(MentorScoreImportedComponentRow component);

    @Select("SELECT category_code AS categoryCode, COALESCE(SUM(score_value), 0) AS scoreValue FROM final_component_score WHERE final_record_id = #{finalRecordId} GROUP BY category_code")
    List<MentorScoreCategoryTotalRow> selectTotals(@Param("finalRecordId") Long finalRecordId);

    @Update("UPDATE final_record SET moral_total = #{moral}, intellectual_total = #{intellectual}, physical_total = #{physical}, labor_total = #{labor}, grand_total = #{grand}, updated_at = #{updatedAt}, version = version + 1 WHERE id = #{finalRecordId} AND status = 'DRAFT'")
    int updateTotals(@Param("finalRecordId") Long finalRecordId,
                     @Param("moral") BigDecimal moral,
                     @Param("intellectual") BigDecimal intellectual,
                     @Param("physical") BigDecimal physical,
                     @Param("labor") BigDecimal labor,
                     @Param("grand") BigDecimal grand,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
