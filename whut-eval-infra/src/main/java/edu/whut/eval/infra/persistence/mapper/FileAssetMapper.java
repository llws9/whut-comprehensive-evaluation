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
}
