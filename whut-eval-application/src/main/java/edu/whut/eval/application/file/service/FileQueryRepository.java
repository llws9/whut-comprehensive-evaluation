package edu.whut.eval.application.file.service;

import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;

import java.util.List;
import java.util.Optional;

public interface FileQueryRepository {

    Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId);

    boolean existsPublishedAllPublicAttachment(String fileId);

    List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode);
}
