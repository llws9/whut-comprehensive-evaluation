package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentQueryDO;
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

    @Select("""
            SELECT COUNT(1)
            FROM public_attachment_entry pae
            JOIN file_asset fa ON fa.file_id = pae.file_id
            WHERE pae.file_id = #{fileId}
              AND pae.status = 'PUBLISHED'
              AND pae.scope_type = 'ALL'
              AND fa.status = 'ACTIVE'
            """)
    int countPublishedAllActiveByFileId(@Param("fileId") String fileId);

    @Select("""
            <script>
            SELECT pae.id AS entry_id,
                   pae.file_id,
                   pae.display_name,
                   pae.description,
                   pae.category_code,
                   fa.original_filename,
                   fa.content_type,
                   fa.size,
                   pae.published_at,
                   pae.sort_no
            FROM public_attachment_entry pae
            JOIN file_asset fa ON fa.file_id = pae.file_id
            WHERE pae.status = 'PUBLISHED'
              AND pae.scope_type = 'ALL'
              AND fa.status = 'ACTIVE'
            <if test='categoryCode != null and categoryCode != ""'>
              AND pae.category_code = #{categoryCode}
            </if>
            ORDER BY pae.sort_no ASC, pae.published_at DESC, pae.id ASC
            </script>
            """)
    List<PublicAttachmentQueryDO> selectPublishedAllActive(@Param("categoryCode") String categoryCode);
}
