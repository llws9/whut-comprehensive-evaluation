package edu.whut.eval.application.file.service;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.command.OfflinePublicAttachmentCommand;
import edu.whut.eval.application.file.command.PublishPublicAttachmentCommand;
import edu.whut.eval.application.file.query.OfflinePublicAttachmentResult;
import edu.whut.eval.application.file.query.PublishPublicAttachmentResult;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class PublicAttachmentCommandApplicationService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final Set<String> SUPPORTED_SCOPE_TYPES = Set.of("ALL", "ORG_UNIT", "ROLE");

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final PublicAttachmentCommandRepository publicAttachmentCommandRepository;

    public PublicAttachmentCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                     PublicAttachmentCommandRepository publicAttachmentCommandRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.publicAttachmentCommandRepository = publicAttachmentCommandRepository;
    }

    @Transactional
    public PublishPublicAttachmentResult publish(PublishPublicAttachmentCommand command) {
        validatePublishCommand(command);
        String fileId = command.fileId().trim();
        publicAttachmentCommandRepository.findActiveFileByFileId(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("文件不存在或已失效: " + fileId));
        if (publicAttachmentCommandRepository.existsActivePublishedEntry(fileId)) {
            throw new ConflictException("相同文件已存在有效发布记录");
        }

        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        LocalDateTime now = LocalDateTime.now();
        String scopeType = normalize(command.scopeType());
        Long entryId = publicAttachmentCommandRepository.publish(new PublicAttachmentCommandRepository.PublishPublicAttachmentRecord(
                fileId,
                command.displayName().trim(),
                trimToNull(command.description()),
                trimToNull(command.categoryCode()),
                scopeType,
                trimToNull(command.scopeValue()),
                authorizationContext.getUserId(),
                now,
                command.sortNo() == null ? 0 : command.sortNo()
        ));
        return new PublishPublicAttachmentResult(entryId, fileId, STATUS_PUBLISHED, scopeType, now);
    }

    @Transactional
    public OfflinePublicAttachmentResult offline(OfflinePublicAttachmentCommand command) {
        if (command.entryId() == null) {
            throw new ValidationException("entryId 不能为空");
        }
        if (isBlank(command.reason())) {
            throw new ValidationException("reason 不能为空");
        }
        PublicAttachmentCommandRepository.PublicAttachmentCommandRecord entry =
                publicAttachmentCommandRepository.findEntryById(command.entryId())
                        .orElseThrow(() -> new ResourceNotFoundException("公共附件记录不存在: " + command.entryId()));
        if (STATUS_OFFLINE.equalsIgnoreCase(entry.status())) {
            throw new ConflictException("记录已下架，无需重复操作");
        }
        if (!STATUS_PUBLISHED.equalsIgnoreCase(entry.status())) {
            throw new ConflictException("仅 PUBLISHED 状态的记录允许下架");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = publicAttachmentCommandRepository.offline(command.entryId(), command.reason().trim(), now);
        if (!updated) {
            throw new ConflictException("公共附件记录状态已变更，请刷新后重试");
        }
        return new OfflinePublicAttachmentResult(command.entryId(), STATUS_OFFLINE, now);
    }

    private void validatePublishCommand(PublishPublicAttachmentCommand command) {
        if (command == null) {
            throw new ValidationException("请求不能为空");
        }
        if (isBlank(command.fileId())) {
            throw new ValidationException("fileId 不能为空");
        }
        if (isBlank(command.displayName())) {
            throw new ValidationException("displayName 不能为空");
        }
        String scopeType = normalize(command.scopeType());
        if (scopeType == null) {
            throw new ValidationException("scopeType 不能为空");
        }
        if (!SUPPORTED_SCOPE_TYPES.contains(scopeType)) {
            throw new ValidationException("scopeType 仅允许 ALL、ORG_UNIT 或 ROLE");
        }
        String scopeValue = trimToNull(command.scopeValue());
        if ("ALL".equals(scopeType) && scopeValue != null) {
            throw new ValidationException("scopeType 为 ALL 时 scopeValue 必须为空");
        }
        if (!"ALL".equals(scopeType) && scopeValue == null) {
            throw new ValidationException("scopeValue 不能为空");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalize(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
