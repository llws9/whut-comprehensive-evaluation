package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentEntryDO;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于 file_asset 和 public_attachment_entry 的申请附件解析器实现。
 * 当前仅放行“本人上传的 ACTIVE 文件”以及“公共池中 PUBLISHED 且 scope_type=ALL 的文件”。
 */
@Service
public class MybatisApplicationAttachmentResolver implements ApplicationAttachmentResolver {

    private static final String FILE_STATUS_ACTIVE = "ACTIVE";
    private static final String PUBLIC_STATUS_PUBLISHED = "PUBLISHED";
    private static final String SCOPE_TYPE_ALL = "ALL";

    private final FileAssetMapper fileAssetMapper;
    private final PublicAttachmentEntryMapper publicAttachmentEntryMapper;

    public MybatisApplicationAttachmentResolver(FileAssetMapper fileAssetMapper,
                                                PublicAttachmentEntryMapper publicAttachmentEntryMapper) {
        this.fileAssetMapper = fileAssetMapper;
        this.publicAttachmentEntryMapper = publicAttachmentEntryMapper;
    }

    /**
     * 逐个 fileId 解析附件元数据，并校验当前用户是否具备绑定权限。
     */
    @Override
    public List<AttachmentRef> resolveForBinding(List<String> attachmentFileIds, Long currentUserId) {
        if (attachmentFileIds == null || attachmentFileIds.isEmpty()) {
            return List.of();
        }
        Map<String, FileAssetDO> fileAssets = fileAssetMapper.selectByFileIds(attachmentFileIds).stream()
                .collect(Collectors.toMap(FileAssetDO::getFileId, Function.identity()));
        Map<String, PublicAttachmentEntryDO> publicEntries = publicAttachmentEntryMapper.selectByFileIds(attachmentFileIds).stream()
                .collect(Collectors.toMap(PublicAttachmentEntryDO::getFileId, Function.identity(), (left, right) -> left));
        return attachmentFileIds.stream()
                .map(fileId -> resolveSingle(fileId, currentUserId, fileAssets, publicEntries))
                .toList();
    }

    private AttachmentRef resolveSingle(String fileId,
                                        Long currentUserId,
                                        Map<String, FileAssetDO> fileAssets,
                                        Map<String, PublicAttachmentEntryDO> publicEntries) {
        FileAssetDO fileAsset = fileAssets.get(fileId);
        if (fileAsset == null || !FILE_STATUS_ACTIVE.equalsIgnoreCase(fileAsset.getStatus())) {
            throw new ValidationException("附件不存在或已失效");
        }
        if (!isOwnedByCurrentUser(fileAsset, currentUserId) && !isPublishedForAll(publicEntries.get(fileId))) {
            throw new ValidationException("当前用户无权使用指定附件");
        }
        return new AttachmentRef(
                fileAsset.getFileId(),
                fileAsset.getStorageKey(),
                fileAsset.getOriginalFilename(),
                fileAsset.getContentType(),
                fileAsset.getSize() == null ? 0L : fileAsset.getSize(),
                fileAsset.getUploaderUserId()
        );
    }

    private boolean isOwnedByCurrentUser(FileAssetDO fileAsset, Long currentUserId) {
        return currentUserId != null && Objects.equals(currentUserId, fileAsset.getUploaderUserId());
    }

    private boolean isPublishedForAll(PublicAttachmentEntryDO publicEntry) {
        return publicEntry != null
                && PUBLIC_STATUS_PUBLISHED.equalsIgnoreCase(publicEntry.getStatus())
                && SCOPE_TYPE_ALL.equalsIgnoreCase(publicEntry.getScopeType());
    }
}
