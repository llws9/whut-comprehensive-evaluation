package edu.whut.eval.application.file.service;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class FileQueryApplicationService {

    private static final String OSS_STORAGE_CONFIG = "oss-storage-config";

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FileQueryRepository fileQueryRepository;
    private final TypedConfigRepository typedConfigRepository;

    public FileQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                       FileQueryRepository fileQueryRepository,
                                       TypedConfigRepository typedConfigRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.fileQueryRepository = fileQueryRepository;
        this.typedConfigRepository = typedConfigRepository;
    }

    public FileMetadataResponse getMetadata(String fileId) {
        FileAssetDescriptor file = requireVisibleFile(fileId);
        return toMetadataResponse(file);
    }

    public FileAccessUrlResponse getAccessUrl(String fileId) {
        return getAccessUrl(fileId, "inline", 300);
    }

    public FileAccessUrlResponse getAccessUrl(String fileId, String disposition, int expireSeconds) {
        validateAccessUrlOptions(disposition, expireSeconds);
        FileAssetDescriptor file = requireVisibleFile(fileId);
        OssStorageConfig config = typedConfigRepository.find(OSS_STORAGE_CONFIG, OssStorageConfig.class)
                .orElseThrow(() -> new ConfigLoadException("Required typed config not found: " + OSS_STORAGE_CONFIG));
        if (config.getPublicBaseUrl() == null || config.getPublicBaseUrl().isBlank()) {
            throw new FileStorageException("无法生成文件访问地址");
        }
        return new FileAccessUrlResponse(file.getFileId(), joinUrl(config.getPublicBaseUrl(), file.getStorageKey()), "PUBLIC_URL", null);
    }

    public List<PublicAttachmentResponse> listPublicAttachments(String categoryCode) {
        return fileQueryRepository.listPublishedAllPublicAttachments(categoryCode).stream()
                .map(this::toPublicAttachmentResponse)
                .toList();
    }

    private FileAssetDescriptor requireVisibleFile(String fileId) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        FileAssetDescriptor file = fileQueryRepository.findActiveFileByFileId(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("文件不存在或已失效: " + fileId));
        if (!Objects.equals(authorizationContext.getUserId(), file.getUploaderUserId())
                && !fileQueryRepository.existsPublishedAllPublicAttachment(fileId)
                && !fileQueryRepository.existsVisibleApplicationBinding(fileId, toApplicationAccessContext(authorizationContext))) {
            throw new AccessDeniedAppException("当前用户无权访问指定文件");
        }
        return file;
    }

    private FileMetadataResponse toMetadataResponse(FileAssetDescriptor file) {
        return new FileMetadataResponse(
                file.getFileId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize() == null ? 0L : file.getSize(),
                file.getStatus(),
                resolveSourceType(file),
                true,
                true,
                file.getCreatedAt()
        );
    }

    private String resolveSourceType(FileAssetDescriptor file) {
        String uploadChannel = file.getUploadChannel();
        if (uploadChannel == null || uploadChannel.isBlank()) {
            return "SELF_UPLOAD";
        }
        return uploadChannel.trim();
    }

    private ApplicationAccessContext toApplicationAccessContext(UserAuthorizationContext authorizationContext) {
        return new ApplicationAccessContext(
                authorizationContext.getUserId(),
                authorizationContext.getUserNo(),
                authorizationContext.getUserName(),
                authorizationContext.getIdentity(),
                authorizationContext.getRoles(),
                authorizationContext.getAuthorities(),
                authorizationContext.getScopeRules(),
                AuthorizationPermissionCodes.APPLICATION_REVIEW
        );
    }

    private void validateAccessUrlOptions(String disposition, int expireSeconds) {
        String normalizedDisposition = disposition == null || disposition.isBlank() ? "inline" : disposition.trim();
        if (!"inline".equalsIgnoreCase(normalizedDisposition) && !"attachment".equalsIgnoreCase(normalizedDisposition)) {
            throw new ValidationException("disposition 仅允许 inline 或 attachment");
        }
        if (expireSeconds <= 0) {
            throw new ValidationException("expireSeconds 必须大于 0");
        }
        if (expireSeconds > 1800) {
            throw new ValidationException("expireSeconds 不能超过 1800");
        }
    }

    private PublicAttachmentResponse toPublicAttachmentResponse(PublicAttachmentDescriptor publicAttachment) {
        return new PublicAttachmentResponse(
                publicAttachment.getEntryId(),
                publicAttachment.getFileId(),
                publicAttachment.getDisplayName(),
                publicAttachment.getDescription(),
                publicAttachment.getCategoryCode(),
                publicAttachment.getOriginalFilename(),
                publicAttachment.getContentType(),
                publicAttachment.getSize() == null ? 0L : publicAttachment.getSize(),
                publicAttachment.getPublishedAt(),
                publicAttachment.getSortNo()
        );
    }

    private String joinUrl(String publicBaseUrl, String storageKey) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String key = storageKey != null && storageKey.startsWith("/")
                ? storageKey.substring(1)
                : storageKey;
        if (key == null || key.isBlank()) {
            throw new FileStorageException("无法生成文件访问地址");
        }
        return base + "/" + key;
    }
}
