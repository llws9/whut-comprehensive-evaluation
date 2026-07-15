package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileAssetMapper {

    /**
     * 按业务文件 ID 批量查询文件主记录。
     */
    @Select({
            "<script>",
            "SELECT id, file_id, storage_key, bucket, original_filename, content_type, size, sha256, ",
            "       uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at ",
            "FROM file_asset ",
            "WHERE file_id IN ",
            "<foreach collection='fileIds' item='fileId' open='(' separator=',' close=')'>",
            "  #{fileId}",
            "</foreach>",
            "</script>"
    })
    List<FileAssetDO> selectByFileIds(@Param("fileIds") List<String> fileIds);

    @Select("""
            SELECT id, file_id, storage_key, bucket, original_filename, content_type, size, sha256,
                   uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at
            FROM file_asset
            WHERE file_id = #{fileId}
              AND status = 'ACTIVE'
            """)
    FileAssetDO selectActiveByFileId(@Param("fileId") String fileId);

    @Select({
            "<script>",
            "SELECT COUNT(1) ",
            "FROM (",
            "  SELECT aa.file_id AS file_id,",
            "         s.application_id AS application_id,",
            "         s.applicant_user_id AS applicant_user_id,",
            "         s.org_unit_id AS org_unit_id,",
            "         o.path AS org_path,",
            "         s.category_code AS category_code,",
            "         s.item_code AS item_code ",
            "  FROM application_attachment aa ",
            "  JOIN application_submission s ON s.application_id = aa.application_id ",
            "  JOIN org_unit o ON o.id = s.org_unit_id ",
            ") application_file_binding ",
            "WHERE file_id = #{fileId} ",
            "  AND (${expression})",
            "</script>"
    })
    long countVisibleApplicationBinding(@Param("fileId") String fileId,
                                        @Param("expression") String expression,
                                        @Param("parameters") java.util.Map<String, Object> parameters);
}
