package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileAssetRegistry;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import edu.whut.eval.infra.persistence.mapper.FileAssetWriteMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 基于 MyBatis 的 file_asset 登记实现。
 */
@Service
public class MybatisFileAssetRegistry implements FileAssetRegistry {

    private static final String FILE_STATUS_ACTIVE = "ACTIVE";
    private static final String UPLOAD_CHANNEL_SELF = "SELF_UPLOAD";

    private final FileAssetWriteMapper fileAssetWriteMapper;

    public MybatisFileAssetRegistry(FileAssetWriteMapper fileAssetWriteMapper) {
        this.fileAssetWriteMapper = fileAssetWriteMapper;
    }

    /**
     * 为已上传文件生成稳定 fileId，并把文件元数据写入 file_asset。
     */
    @Override
    public StoredFileDescriptor registerUploadedFile(StoredFileDescriptor descriptor,
                                                     Long uploaderUserId,
                                                     String uploaderIdentity) {
        String fileId = generateFileId();
        LocalDateTime now = LocalDateTime.now();
        FileAssetDO fileAssetDO = new FileAssetDO();
        fileAssetDO.setFileId(fileId);
        fileAssetDO.setStorageKey(descriptor.getObjectKey());
        fileAssetDO.setBucket(descriptor.getBucket());
        fileAssetDO.setOriginalFilename(descriptor.getOriginalFilename());
        fileAssetDO.setContentType(descriptor.getContentType());
        fileAssetDO.setSize(descriptor.getSize());
        fileAssetDO.setUploaderUserId(uploaderUserId);
        fileAssetDO.setUploaderType(resolveUploaderType(uploaderIdentity));
        fileAssetDO.setUploadChannel(UPLOAD_CHANNEL_SELF);
        fileAssetDO.setStatus(FILE_STATUS_ACTIVE);
        fileAssetDO.setCreatedAt(now);
        fileAssetDO.setUpdatedAt(now);
        try {
            fileAssetWriteMapper.insert(fileAssetDO);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("上传文件登记冲突，请重试");
        }
        return new StoredFileDescriptor(
                fileId,
                descriptor.getBucket(),
                descriptor.getObjectKey(),
                descriptor.getPublicUrl(),
                descriptor.getOriginalFilename(),
                descriptor.getContentType(),
                descriptor.getSize()
        );
    }

    private String generateFileId() {
        return "file_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveUploaderType(String uploaderIdentity) {
        if (uploaderIdentity == null || uploaderIdentity.isBlank()) {
            return "USER";
        }
        String normalized = uploaderIdentity.toUpperCase(Locale.ROOT);
        if (normalized.contains("ADMIN")) {
            return "ADMIN";
        }
        if (normalized.contains("SYSTEM")) {
            return "SYSTEM";
        }
        return "USER";
    }
}
