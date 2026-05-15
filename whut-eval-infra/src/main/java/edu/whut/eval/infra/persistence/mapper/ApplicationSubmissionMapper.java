package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.ApplicationSubmissionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApplicationSubmissionMapper {

    /**
     * 按主键读取申请主表。
     */
    @Select("SELECT application_id, applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version FROM application_submission WHERE application_id = #{applicationId}")
    ApplicationSubmissionDO selectById(@Param("applicationId") Long applicationId);

    /**
     * 插入申请主表并回填主键。
     */
    @Insert("INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version) VALUES (#{applicantUserId}, #{orgUnitId}, #{categoryCode}, #{itemCode}, #{academicYear}, #{term}, #{title}, #{description}, #{status}, #{submittedAt}, #{createdAt}, #{updatedAt}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "applicationId")
    int insert(ApplicationSubmissionDO applicationSubmissionDO);

    /**
     * 基于主键和旧版本执行乐观锁更新。
     */
    @Update("UPDATE application_submission SET org_unit_id = #{application.orgUnitId}, category_code = #{application.categoryCode}, item_code = #{application.itemCode}, academic_year = #{application.academicYear}, term = #{application.term}, title = #{application.title}, description = #{application.description}, status = #{application.status}, submitted_at = #{application.submittedAt}, updated_at = #{application.updatedAt}, version = #{application.version} WHERE application_id = #{application.applicationId} AND version = #{previousVersion}")
    int updateWithVersion(@Param("application") ApplicationSubmissionDO applicationSubmissionDO,
                          @Param("previousVersion") Long previousVersion);

    /**
     * 判断是否存在相同项目和学期下的活跃申请。
     */
    @Select("SELECT COUNT(1) > 0 FROM application_submission WHERE applicant_user_id = #{applicantUserId} AND item_code = #{itemCode} AND academic_year = #{academicYear} AND term = #{term} AND status IN ('DRAFT', 'SUBMITTED', 'RETURNED') AND (#{excludeApplicationId} IS NULL OR application_id <> #{excludeApplicationId})")
    boolean existsActiveSubmission(@Param("applicantUserId") Long applicantUserId,
                                   @Param("itemCode") String itemCode,
                                   @Param("academicYear") String academicYear,
                                   @Param("term") String term,
                                   @Param("excludeApplicationId") Long excludeApplicationId);
}
