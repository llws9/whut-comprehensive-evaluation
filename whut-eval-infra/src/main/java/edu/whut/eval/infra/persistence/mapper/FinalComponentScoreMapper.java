package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.FinalComponentScoreDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinalComponentScoreMapper {

    @Delete("DELETE FROM final_component_score WHERE final_record_id = #{finalRecordId}")
    int deleteByFinalRecordId(@Param("finalRecordId") Long finalRecordId);

    @Insert("INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at) VALUES (#{finalRecordId}, #{categoryCode}, #{itemCode}, #{scoreValue}, #{displayText}, #{sourceType}, #{sourceRefId}, #{createdAt})")
    int insert(FinalComponentScoreDO component);

    @Select("SELECT id, final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at FROM final_component_score WHERE final_record_id = #{finalRecordId} ORDER BY category_code ASC, item_code ASC, id ASC")
    List<FinalComponentScoreDO> selectByFinalRecordId(@Param("finalRecordId") Long finalRecordId);
}
