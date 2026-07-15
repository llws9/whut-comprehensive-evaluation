package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.service.PublicAttachmentCommandRepository;
import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentEntryDO;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MybatisPublicAttachmentCommandRepository implements PublicAttachmentCommandRepository {

    private final FileAssetMapper fileAssetMapper;
    private final PublicAttachmentEntryMapper publicAttachmentEntryMapper;

    public MybatisPublicAttachmentCommandRepository(FileAssetMapper fileAssetMapper,
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
    public boolean existsActivePublishedEntry(String fileId) {
        return publicAttachmentEntryMapper.countActivePublishedByFileId(fileId) > 0;
    }

    @Override
    public Long publish(PublishPublicAttachmentRecord record) {
        PublicAttachmentEntryMapper.PublishPublicAttachmentSqlRecord sqlRecord =
                new PublicAttachmentEntryMapper.PublishPublicAttachmentSqlRecord(
                        record.fileId(),
                        record.displayName(),
                        record.description(),
                        record.categoryCode(),
                        record.scopeType(),
                        record.scopeValue(),
                        record.publishedBy(),
                        record.publishedAt(),
                        record.sortNo()
                );
        publicAttachmentEntryMapper.insertPublished(sqlRecord);
        return sqlRecord.id();
    }

    @Override
    public Optional<PublicAttachmentCommandRecord> findEntryById(Long entryId) {
        return publicAttachmentEntryMapper.selectById(entryId)
                .map(this::toCommandRecord);
    }

    @Override
    public boolean offline(Long entryId, String reason, LocalDateTime offlineAt) {
        return publicAttachmentEntryMapper.offlineById(entryId, reason, offlineAt) > 0;
    }

    private FileAssetDescriptor toFileAssetDescriptor(FileAssetDO fileAsset) {
        return new FileAssetDescriptor(
                fileAsset.getFileId(),
                fileAsset.getStorageKey(),
                fileAsset.getOriginalFilename(),
                fileAsset.getContentType(),
                fileAsset.getSize(),
                fileAsset.getUploaderUserId(),
                fileAsset.getUploadChannel(),
                fileAsset.getStatus(),
                fileAsset.getCreatedAt()
        );
    }

    private PublicAttachmentCommandRecord toCommandRecord(PublicAttachmentEntryDO entry) {
        return new PublicAttachmentCommandRecord(
                entry.getId(),
                entry.getFileId(),
                entry.getDisplayName(),
                entry.getDescription(),
                entry.getCategoryCode(),
                entry.getScopeType(),
                entry.getScopeValue(),
                entry.getStatus()
        );
    }
}
