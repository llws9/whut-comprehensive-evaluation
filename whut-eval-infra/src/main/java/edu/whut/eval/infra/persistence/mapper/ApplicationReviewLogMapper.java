package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.ApplicationReviewLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationReviewLogMapper {

    @Insert("INSERT INTO application_review_log (application_id, action, reviewer_id, review_role, reason, reviewed_at) VALUES (#{applicationId}, #{action}, #{reviewerId}, #{reviewRole}, #{reason}, #{reviewedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApplicationReviewLogDO reviewLog);

    @Select("SELECT id, application_id, action, reviewer_id, review_role, reason, reviewed_at FROM application_review_log WHERE application_id = #{applicationId} ORDER BY reviewed_at ASC, id ASC")
    List<ApplicationReviewLogDO> selectByApplicationId(@Param("applicationId") Long applicationId);
}
