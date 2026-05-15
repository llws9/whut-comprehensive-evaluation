package edu.whut.eval.infra.persistence.mapper;

import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface FileAssetWriteMapper {

    /**
     * 插入单条文件主记录。
     */
    @Insert("INSERT INTO file_asset (file_id, storage_key, bucket, original_filename, content_type, size, sha256, uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at) " +
            "VALUES (#{fileId}, #{storageKey}, #{bucket}, #{originalFilename}, #{contentType}, #{size}, #{sha256}, #{uploaderUserId}, #{uploaderType}, #{uploadChannel}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileAssetDO fileAssetDO);
}
