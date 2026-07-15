package edu.whut.eval.application.file.service;

import edu.whut.eval.application.file.query.FileAssetDescriptor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PublicAttachmentCommandRepository {

    Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId);

    boolean existsActivePublishedEntry(String fileId);

    Long publish(PublishPublicAttachmentRecord record);

    Optional<PublicAttachmentCommandRecord> findEntryById(Long entryId);

    boolean offline(Long entryId, String reason, LocalDateTime offlineAt);

    record PublishPublicAttachmentRecord(
            String fileId,
            String displayName,
            String description,
            String categoryCode,
            String scopeType,
            String scopeValue,
            Long publishedBy,
            LocalDateTime publishedAt,
            Integer sortNo
    ) {
    }

    record PublicAttachmentCommandRecord(
            Long entryId,
            String fileId,
            String displayName,
            String description,
            String categoryCode,
            String scopeType,
            String scopeValue,
            String status
    ) {
    }
}
