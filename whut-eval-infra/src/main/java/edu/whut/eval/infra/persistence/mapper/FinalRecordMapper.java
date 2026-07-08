package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FinalRecordMapper {

    @Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE id = #{id}")
    FinalRecordDO selectById(@Param("id") Long id);

    @Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE student_user_id = #{studentUserId} AND academic_year = #{academicYear}")
    FinalRecordDO selectByStudentAndAcademicYear(@Param("studentUserId") Long studentUserId,
                                                 @Param("academicYear") String academicYear);

    @Insert("INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at) VALUES (#{studentUserId}, #{academicYear}, #{status}, #{moralTotal}, #{intellectualTotal}, #{physicalTotal}, #{laborTotal}, #{grandTotal}, #{submittedAt}, #{confirmedAt}, #{confirmComment}, #{version}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FinalRecordDO record);

    @Update("UPDATE final_record SET status = #{record.status}, submitted_at = #{record.submittedAt}, confirmed_at = #{record.confirmedAt}, confirm_comment = #{record.confirmComment}, version = #{record.version}, updated_at = #{record.updatedAt} WHERE id = #{record.id} AND version = #{previousVersion}")
    int updateTransition(@Param("record") FinalRecordDO record, @Param("previousVersion") Long previousVersion);

    @Delete("DELETE FROM final_record WHERE id = #{id} AND status = 'DRAFT'")
    int deleteDraft(@Param("id") Long id);
}
