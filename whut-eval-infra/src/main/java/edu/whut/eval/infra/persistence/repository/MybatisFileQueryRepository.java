package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.application.file.service.FileQueryRepository;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.domain.auth.service.ScopePredicateBuilder;
import edu.whut.eval.infra.persistence.dataobject.FileAssetDO;
import edu.whut.eval.infra.persistence.dataobject.PublicAttachmentQueryDO;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisFileQueryRepository implements FileQueryRepository {

    private final FileAssetMapper fileAssetMapper;
    private final PublicAttachmentEntryMapper publicAttachmentEntryMapper;
    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopePredicateBuilder scopePredicateBuilder;
    private final ApplicationScopeSqlTranslator applicationScopeSqlTranslator;

    public MybatisFileQueryRepository(FileAssetMapper fileAssetMapper,
                                      PublicAttachmentEntryMapper publicAttachmentEntryMapper,
                                      AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                      ScopePredicateBuilder scopePredicateBuilder,
                                      ApplicationScopeSqlTranslator applicationScopeSqlTranslator) {
        this.fileAssetMapper = fileAssetMapper;
        this.publicAttachmentEntryMapper = publicAttachmentEntryMapper;
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopePredicateBuilder = scopePredicateBuilder;
        this.applicationScopeSqlTranslator = applicationScopeSqlTranslator;
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
    public boolean existsVisibleApplicationBinding(String fileId, ApplicationAccessContext accessContext) {
        SqlPredicateFragment fragment = scopeFragment(accessContext);
        String expression = fragment.getExpression();
        if (expression == null || expression.isBlank()) {
            expression = "1 = 1";
        }
        return fileAssetMapper.countVisibleApplicationBinding(fileId, expression, fragment.getParameters()) > 0;
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
                fileAsset.getUploadChannel(),
                fileAsset.getStatus(),
                fileAsset.getCreatedAt()
        );
    }

    private SqlPredicateFragment scopeFragment(ApplicationAccessContext accessContext) {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, accessContext.getPermissionCode());
        ApplicationScopePredicate predicate = scopePredicateBuilder.buildForApplication(authorizationContext, scopeSet);
        return applicationScopeSqlTranslator.translate(authorizationContext, predicate);
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
