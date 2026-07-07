package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.ApplicationFactDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationFactMapper {

    @Select("SELECT id, application_id AS applicationId, score_value AS scoreValue, display_text AS displayText, evidence_count AS evidenceCount, extra_json AS extraJson, created_at AS createdAt, updated_at AS updatedAt FROM application_fact WHERE application_id = #{applicationId}")
    ApplicationFactDO selectLatestByApplicationId(@Param("applicationId") Long applicationId);

    @Delete("DELETE FROM application_fact WHERE application_id = #{applicationId}")
    int deleteByApplicationId(@Param("applicationId") Long applicationId);

    @Insert("INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at) VALUES (#{applicationId}, #{scoreValue}, #{displayText}, #{evidenceCount}, #{extraJson}, #{createdAt}, #{updatedAt})")
    int insert(ApplicationFactDO applicationFactDO);
}
