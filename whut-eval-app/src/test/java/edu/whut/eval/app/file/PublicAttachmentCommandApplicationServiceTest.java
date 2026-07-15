package edu.whut.eval.app.file;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.command.OfflinePublicAttachmentCommand;
import edu.whut.eval.application.file.command.PublishPublicAttachmentCommand;
import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.OfflinePublicAttachmentResult;
import edu.whut.eval.application.file.query.PublishPublicAttachmentResult;
import edu.whut.eval.application.file.service.PublicAttachmentCommandApplicationService;
import edu.whut.eval.application.file.service.PublicAttachmentCommandRepository;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicAttachmentCommandApplicationServiceTest {

    private final StubUserAuthorizationContextAssembler authorizationContextAssembler =
            new StubUserAuthorizationContextAssembler(1012L);
    private final StubPublicAttachmentCommandRepository repository = new StubPublicAttachmentCommandRepository();
    private final PublicAttachmentCommandApplicationService service =
            new PublicAttachmentCommandApplicationService(authorizationContextAssembler, repository);

    @Test
    void shouldPublishActiveFileAsPublicAttachment() {
        repository.file = file("FILE-NEW", "ACTIVE");
        repository.nextEntryId = 15001L;

        PublishPublicAttachmentResult result = service.publish(new PublishPublicAttachmentCommand(
                "FILE-NEW",
                "综测模板",
                "学生填写说明",
                "INTELLECTUAL",
                "ALL",
                null,
                10
        ));

        assertThat(result.getEntryId()).isEqualTo(15001L);
        assertThat(result.getFileId()).isEqualTo("FILE-NEW");
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getScopeType()).isEqualTo("ALL");
        assertThat(result.getPublishedAt()).isNotNull();
        assertThat(repository.savedCommand.fileId()).isEqualTo("FILE-NEW");
        assertThat(repository.savedCommand.displayName()).isEqualTo("综测模板");
        assertThat(repository.savedCommand.publishedBy()).isEqualTo(1012L);
        assertThat(repository.savedCommand.sortNo()).isEqualTo(10);
    }

    @Test
    void shouldRejectPublishingMissingFile() {
        repository.file = null;

        assertThatThrownBy(() -> service.publish(new PublishPublicAttachmentCommand(
                "FILE-MISSING", "模板", null, "MORAL", "ALL", null, 0
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文件不存在或已失效: FILE-MISSING");
    }

    @Test
    void shouldRejectDuplicatePublishedFile() {
        repository.file = file("FILE-DUP", "ACTIVE");
        repository.hasActivePublishedEntry = true;

        assertThatThrownBy(() -> service.publish(new PublishPublicAttachmentCommand(
                "FILE-DUP", "模板", null, "MORAL", "ALL", null, 0
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("相同文件已存在有效发布记录");
    }

    @Test
    void shouldValidateScopeValueByScopeType() {
        repository.file = file("FILE-SCOPED", "ACTIVE");

        assertThatThrownBy(() -> service.publish(new PublishPublicAttachmentCommand(
                "FILE-SCOPED", "模板", null, "MORAL", "ORG_UNIT", null, 0
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scopeValue 不能为空");

        assertThatThrownBy(() -> service.publish(new PublishPublicAttachmentCommand(
                "FILE-SCOPED", "模板", null, "MORAL", "ALL", "2010", 0
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("scopeType 为 ALL 时 scopeValue 必须为空");
    }

    @Test
    void shouldOfflinePublishedEntry() {
        repository.entry = entry(15001L, "FILE-OLD", "PUBLISHED");

        OfflinePublicAttachmentResult result = service.offline(new OfflinePublicAttachmentCommand(15001L, "资料过期"));

        assertThat(result.getEntryId()).isEqualTo(15001L);
        assertThat(result.getStatus()).isEqualTo("OFFLINE");
        assertThat(result.getOfflineAt()).isNotNull();
        assertThat(repository.offlineEntryId).isEqualTo(15001L);
        assertThat(repository.offlineReason).isEqualTo("资料过期");
    }

    @Test
    void shouldRejectOfflineAlreadyOfflineEntry() {
        repository.entry = entry(15001L, "FILE-OLD", "OFFLINE");

        assertThatThrownBy(() -> service.offline(new OfflinePublicAttachmentCommand(15001L, "重复下架")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("记录已下架，无需重复操作");
    }

    @Test
    void shouldRejectOfflineDraftEntry() {
        repository.entry = entry(15001L, "FILE-DRAFT", "DRAFT");

        assertThatThrownBy(() -> service.offline(new OfflinePublicAttachmentCommand(15001L, "草稿不可下架")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("仅 PUBLISHED 状态的记录允许下架");
    }

    private static FileAssetDescriptor file(String fileId, String status) {
        return new FileAssetDescriptor(
                fileId,
                "attachments/" + fileId + ".pdf",
                fileId + ".pdf",
                "application/pdf",
                128L,
                9001L,
                "ADMIN_UPLOAD",
                status,
                LocalDateTime.parse("2026-07-06T10:00:00")
        );
    }

    private static PublicAttachmentCommandRepository.PublicAttachmentCommandRecord entry(Long entryId,
                                                                                         String fileId,
                                                                                         String status) {
        return new PublicAttachmentCommandRepository.PublicAttachmentCommandRecord(
                entryId,
                fileId,
                "模板",
                "说明",
                "INTELLECTUAL",
                "ALL",
                null,
                status
        );
    }

    private static class StubUserAuthorizationContextAssembler implements UserAuthorizationContextAssembler {

        private final Long userId;

        private StubUserAuthorizationContextAssembler(Long userId) {
            this.userId = userId;
        }

        @Override
        public Optional<UserAuthorizationContext> currentAuthorizationContext() {
            return Optional.of(new UserAuthorizationContext(
                    userId,
                    "A0012",
                    "附件管理员",
                    "ADMIN",
                    Set.of("ADMIN"),
                    Set.of("attachment.pool.publish", "attachment.pool.offline"),
                    java.util.List.of()
            ));
        }
    }

    private static class StubPublicAttachmentCommandRepository implements PublicAttachmentCommandRepository {

        private FileAssetDescriptor file;
        private boolean hasActivePublishedEntry;
        private Long nextEntryId;
        private PublishPublicAttachmentRecord savedCommand;
        private PublicAttachmentCommandRecord entry;
        private Long offlineEntryId;
        private String offlineReason;

        @Override
        public Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId) {
            return Optional.ofNullable(file);
        }

        @Override
        public boolean existsActivePublishedEntry(String fileId) {
            return hasActivePublishedEntry;
        }

        @Override
        public Long publish(PublishPublicAttachmentRecord record) {
            savedCommand = record;
            return nextEntryId;
        }

        @Override
        public Optional<PublicAttachmentCommandRecord> findEntryById(Long entryId) {
            return Optional.ofNullable(entry);
        }

        @Override
        public boolean offline(Long entryId, String reason, LocalDateTime offlineAt) {
            offlineEntryId = entryId;
            offlineReason = reason;
            return true;
        }
    }
}
