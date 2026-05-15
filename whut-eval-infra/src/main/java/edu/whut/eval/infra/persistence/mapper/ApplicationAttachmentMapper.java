package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.ApplicationAttachmentDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationAttachmentMapper {

    /**
     * 读取申请下的所有附件。
     */
    @Select("SELECT id, application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no FROM application_attachment WHERE application_id = #{applicationId} ORDER BY sort_no ASC, id ASC")
    List<ApplicationAttachmentDO> selectByApplicationId(@Param("applicationId") Long applicationId);

    /**
     * 删除申请下的全部附件，便于使用完整替换语义。
     */
    @Delete("DELETE FROM application_attachment WHERE application_id = #{applicationId}")
    int deleteByApplicationId(@Param("applicationId") Long applicationId);

    /**
     * 插入单条申请附件。
     */
    @Insert("INSERT INTO application_attachment (application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no) VALUES (#{applicationId}, #{fileId}, #{storageKey}, #{originalFilename}, #{contentType}, #{size}, #{uploadedBy}, #{sortNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApplicationAttachmentDO applicationAttachmentDO);
}
