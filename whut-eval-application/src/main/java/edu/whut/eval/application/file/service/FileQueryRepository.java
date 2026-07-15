package edu.whut.eval.application.file.service;

import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;

import java.util.List;
import java.util.Optional;

public interface FileQueryRepository {

    Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId);

    boolean existsPublishedAllPublicAttachment(String fileId);

    boolean existsVisibleApplicationBinding(String fileId, ApplicationAccessContext accessContext);

    default boolean existsVisibleApplicationBinding(String fileId, Long userId) {
        return existsVisibleApplicationBinding(fileId, new ApplicationAccessContext(
                userId,
                null,
                null,
                null,
                java.util.Set.of(),
                java.util.Set.of(edu.whut.eval.application.auth.AuthorizationPermissionCodes.APPLICATION_VIEW_SELF),
                java.util.List.of(new edu.whut.eval.domain.iam.model.IamScopeRule(
                        null,
                        edu.whut.eval.application.auth.AuthorizationPermissionCodes.APPLICATION_VIEW_SELF,
                        "SELF",
                        null,
                        null,
                        null,
                        null,
                        10,
                        "ACTIVE"
                )),
                edu.whut.eval.application.auth.AuthorizationPermissionCodes.APPLICATION_VIEW_SELF
        ));
    }

    List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode);
}
