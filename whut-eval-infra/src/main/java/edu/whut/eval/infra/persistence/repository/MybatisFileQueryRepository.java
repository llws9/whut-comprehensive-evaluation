package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.application.file.service.FileQueryRepository;
import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentQueryDO;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisFileQueryRepository implements FileQueryRepository {

    private final FileAssetMapper fileAssetMapper;
    private final PublicAttachmentEntryMapper publicAttachmentEntryMapper;

    public MybatisFileQueryRepository(FileAssetMapper fileAssetMapper,
                                      PublicAttachmentEntryMapper publicAttachmentEntryMapper) {
        this.fileAssetMapper = fileAssetMapper;
        this.publicAttachmentEntryMapper = publicAttachmentEntryMapper;
    }

    @Override
    public Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId) {
        return Optional.ofNullable(fileAssetMapper.selectActiveByFileId(fileId))
                .map(this::toFileAssetDescriptor);
    }

    @Override
    public boolean existsPublishedAllPublicAttachment(String fileId) {
        return publicAttachmentEntryMapper.countPublishedAllActiveByFileId(fileId) > 0;
    }

    @Override
    public List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode) {
        return publicAttachmentEntryMapper.selectPublishedAllActive(categoryCode).stream()
                .map(this::toPublicAttachmentDescriptor)
                .toList();
    }

    private FileAssetDescriptor toFileAssetDescriptor(FileAssetDO fileAsset) {
        return new FileAssetDescriptor(
                fileAsset.getFileId(),
                fileAsset.getStorageKey(),
                fileAsset.getOriginalFilename(),
                fileAsset.getContentType(),
                fileAsset.getSize(),
                fileAsset.getUploaderUserId(),
                fileAsset.getStatus(),
                fileAsset.getCreatedAt()
        );
    }

    private PublicAttachmentDescriptor toPublicAttachmentDescriptor(PublicAttachmentQueryDO publicAttachment) {
        return new PublicAttachmentDescriptor(
                publicAttachment.getEntryId(),
                publicAttachment.getFileId(),
                publicAttachment.getDisplayName(),
                publicAttachment.getDescription(),
                publicAttachment.getCategoryCode(),
                publicAttachment.getOriginalFilename(),
                publicAttachment.getContentType(),
                publicAttachment.getSize(),
                publicAttachment.getPublishedAt(),
                publicAttachment.getSortNo()
        );
    }
}
