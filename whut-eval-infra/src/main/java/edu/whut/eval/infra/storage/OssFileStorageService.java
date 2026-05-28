package edu.whut.eval.infra.storage;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileStorageService;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.config.OssStorageConfigProvider;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 基于 OSS 的文件存储服务实现。
 */
@Service
public class OssFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Logger log = LoggerFactory.getLogger(OssFileStorageService.class);

    private final OssStorageConfigProvider ossStorageConfigProvider;
    private final OssObjectStorageClient ossObjectStorageClient;

    public OssFileStorageService(OssStorageConfigProvider ossStorageConfigProvider,
                                 OssObjectStorageClient ossObjectStorageClient) {
        this.ossStorageConfigProvider = ossStorageConfigProvider;
        this.ossObjectStorageClient = ossObjectStorageClient;
    }

    @Override
    public StoredFileDescriptor store(UploadFileCommand command) {
        AppLog.info(log, "file.upload.storage.started",
                "bizType", command.getBizType(),
                "originalFilename", command.getOriginalFilename(),
                "contentType", command.getContentType(),
                "size", command.getSize());
        validateCommand(command);
        OssStorageConfig config;
        try {
            config = ossStorageConfigProvider.requiredConfig();
        } catch (ConfigLoadException exception) {
            AppLog.error(log, exception, "file.upload.storage.config-missing",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "size", command.getSize());
            throw exception;
        }
        if (!config.isEnabled()) {
            AppLog.warn(log, "file.upload.storage.disabled",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "bucket", config.getBucket());
            throw new ValidationException("OSS 文件存储当前未启用");
        }
        String objectKey = buildObjectKey(config, command);
        StoredOssObject storedObject = ossObjectStorageClient.putObject(
                config,
                objectKey,
                command.getInputStream(),
                command.getSize(),
                command.getContentType()
        );
        StoredFileDescriptor descriptor = new StoredFileDescriptor(
                storedObject.getBucket(),
                storedObject.getObjectKey(),
                storedObject.getPublicUrl(),
                command.getOriginalFilename(),
                command.getContentType(),
                command.getSize()
        );
        AppLog.info(log, "file.upload.storage.completed",
                "bizType", command.getBizType(),
                "bucket", descriptor.getBucket(),
                "objectKey", descriptor.getObjectKey(),
                "contentType", descriptor.getContentType(),
                "size", descriptor.getSize());
        return descriptor;
    }

    /**
     * 统一生成对象 key，保证上传路径规则在全项目内保持一致。
     */
    String buildObjectKey(OssStorageConfig config, UploadFileCommand command) {
        String normalizedPrefix = normalizeSegment(config.getKeyPrefix(), "uploads");
        String normalizedBiz = normalizeSegment(command.getBizType(), "common");
        String normalizedFilename = sanitizeFilename(command.getOriginalFilename());
        return normalizedPrefix
                + "/"
                + normalizedBiz
                + "/"
                + DATE_FORMATTER.format(LocalDate.now())
                + "/"
                + UUID.randomUUID()
                + "-"
                + normalizedFilename;
    }

    private void validateCommand(UploadFileCommand command) {
        if (command.getInputStream() == null) {
            AppLog.warn(log, "file.upload.storage.validation-failed",
                    "reason", "input-stream-null",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "size", command.getSize());
            throw new ValidationException("上传文件流不能为空");
        }
        if (command.getSize() <= 0) {
            AppLog.warn(log, "file.upload.storage.validation-failed",
                    "reason", "size-non-positive",
                    "bizType", command.getBizType(),
                    "originalFilename", command.getOriginalFilename(),
                    "size", command.getSize());
            throw new ValidationException("上传文件不能为空");
        }
        if (command.getOriginalFilename() == null || command.getOriginalFilename().isBlank()) {
            AppLog.warn(log, "file.upload.storage.validation-failed",
                    "reason", "filename-blank",
                    "bizType", command.getBizType(),
                    "size", command.getSize());
            throw new ValidationException("上传文件名不能为空");
        }
    }

    private String normalizeSegment(String value, String defaultValue) {
        String resolved = (value == null || value.isBlank()) ? defaultValue : value.trim();
        resolved = resolved.replace('\\', '/');
        while (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        while (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved.isBlank() ? defaultValue : resolved;
    }

    private String sanitizeFilename(String originalFilename) {
        String trimmed = originalFilename.trim();
        int lastSlashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (lastSlashIndex >= 0) {
            trimmed = trimmed.substring(lastSlashIndex + 1);
        }
        String sanitized = trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.toLowerCase(Locale.ROOT);
    }
}
