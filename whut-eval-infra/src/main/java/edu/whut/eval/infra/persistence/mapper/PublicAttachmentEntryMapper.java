package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentEntryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PublicAttachmentEntryMapper {

    /**
     * 按文件 ID 批量查询公共附件池发布记录。
     */
    @Select({
            "<script>",
            "SELECT id, file_id, display_name, description, category_code, scope_type, scope_value, status, ",
            "       published_by, published_at, sort_no, created_at, updated_at ",
            "FROM public_attachment_entry ",
            "WHERE file_id IN ",
            "<foreach collection='fileIds' item='fileId' open='(' separator=',' close=')'>",
            "  #{fileId}",
            "</foreach>",
            "</script>"
    })
    List<PublicAttachmentEntryDO> selectByFileIds(@Param("fileIds") List<String> fileIds);
}
