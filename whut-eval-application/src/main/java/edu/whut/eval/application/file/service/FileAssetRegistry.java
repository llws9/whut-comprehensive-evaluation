package edu.whut.eval.application.file.service;

import edu.whut.eval.application.file.query.StoredFileDescriptor;

/**
 * 文件元数据登记抽象。
 * 负责在对象存储上传成功后，将文件写入业务侧 file_asset，并返回携带 fileId 的结果。
 */
public interface FileAssetRegistry {

    /**
     * 为已上传文件登记业务文件 ID 和上传元数据。
     */
    StoredFileDescriptor registerUploadedFile(StoredFileDescriptor descriptor,
                                             Long uploaderUserId,
                                             String uploaderIdentity);
}
