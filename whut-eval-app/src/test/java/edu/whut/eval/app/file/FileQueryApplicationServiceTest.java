package edu.whut.eval.app.file;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.application.file.service.FileQueryRepository;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import edu.whut.eval.infra.nacos.config.NacosTypedConfigConfiguration;
import edu.whut.eval.infra.nacos.config.OssStorageConfigProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileQueryApplicationServiceTest {

    private final StubUserAuthorizationContextAssembler authorizationContextAssembler =
            new StubUserAuthorizationContextAssembler(1001L);
    private final StubFileQueryRepository fileQueryRepository = new StubFileQueryRepository();
    private final TypedConfigRepository typedConfigRepository = new NacosTypedConfigConfiguration().typedConfigRepository();
    private final FileQueryApplicationService service = new FileQueryApplicationService(
            authorizationContextAssembler,
            fileQueryRepository,
            typedConfigRepository
    );

    @Test
    void shouldReturnOwnerActiveFileMetadata() {
        fileQueryRepository.file = file("file-own", "uploads/own.pdf", 1001L, "ACTIVE");

        FileMetadataResponse response = service.getMetadata("file-own");

        assertThat(response.getFileId()).isEqualTo("file-own");
        assertThat(response.getOriginalFilename()).isEqualTo("file-own.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSize()).isEqualTo(128L);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getSourceType()).isEqualTo("SELF_UPLOAD");
        assertThat(response.isCanPreview()).isTrue();
        assertThat(response.isCanDownload()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-07-06T10:00:00"));
    }

    @Test
    void shouldReturnPublishedAllPublicFileMetadataForOtherUser() {
        fileQueryRepository.file = file("file-public", "uploads/public.pdf", 9001L, "ACTIVE");
        fileQueryRepository.publicVisible = true;

        FileMetadataResponse response = service.getMetadata("file-public");

        assertThat(response.getFileId()).isEqualTo("file-public");
    }

    @Test
    void shouldDenyPrivateFileOwnedByAnotherUser() {
        fileQueryRepository.file = file("file-private", "uploads/private.pdf", 9001L, "ACTIVE");

        assertThatThrownBy(() -> service.getMetadata("file-private"))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权访问指定文件");
    }

    @Test
    void shouldAllowFileBoundToVisibleApplicationForReviewer() {
        fileQueryRepository.file = file("file-bound", "uploads/bound.pdf", 9001L, "ACTIVE");
        fileQueryRepository.applicationBoundVisible = true;

        FileMetadataResponse response = service.getMetadata("file-bound");

        assertThat(response.getFileId()).isEqualTo("file-bound");
        assertThat(response.getSourceType()).isEqualTo("SELF_UPLOAD");
        assertThat(fileQueryRepository.lastBoundUserId).isEqualTo(1001L);
    }

    @Test
    void shouldReturnNotFoundWhenActiveFileDoesNotExist() {
        fileQueryRepository.file = null;

        assertThatThrownBy(() -> service.getMetadata("file-missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文件不存在或已失效: file-missing");
    }

    @Test
    void shouldBuildAccessUrlWithExactlyOneSlash() {
        fileQueryRepository.file = file("file-own", "/uploads/own.pdf", 1001L, "ACTIVE");
        OssStorageConfig config = new OssStorageConfig();
        config.setPublicBaseUrl("https://cdn.example.com/base/");
        typedConfigRepository.save(OssStorageConfigProvider.DEFINITION_NAME, config);

        FileAccessUrlResponse response = service.getAccessUrl("file-own", "attachment", 600);

        assertThat(response.getFileId()).isEqualTo("file-own");
        assertThat(response.getAccessUrl()).isEqualTo("https://cdn.example.com/base/uploads/own.pdf");
        assertThat(response.getAccessMode()).isEqualTo("PUBLIC_URL");
        assertThat(response.getExpiresAt()).isNull();
    }

    @Test
    void shouldRejectUnsupportedDisposition() {
        fileQueryRepository.file = file("file-own", "uploads/own.pdf", 1001L, "ACTIVE");

        assertThatThrownBy(() -> service.getAccessUrl("file-own", "open", 300))
                .isInstanceOf(edu.whut.eval.common.exception.ValidationException.class)
                .hasMessage("disposition 仅允许 inline 或 attachment");
    }

    @Test
    void shouldRejectTooLargeExpireSeconds() {
        fileQueryRepository.file = file("file-own", "uploads/own.pdf", 1001L, "ACTIVE");

        assertThatThrownBy(() -> service.getAccessUrl("file-own", "inline", 1801))
                .isInstanceOf(edu.whut.eval.common.exception.ValidationException.class)
                .hasMessage("expireSeconds 不能超过 1800");
    }

    @Test
    void shouldFailWhenPublicBaseUrlIsBlank() {
        fileQueryRepository.file = file("file-own", "uploads/own.pdf", 1001L, "ACTIVE");
        OssStorageConfig config = new OssStorageConfig();
        config.setPublicBaseUrl(" ");
        typedConfigRepository.save(OssStorageConfigProvider.DEFINITION_NAME, config);

        assertThatThrownBy(() -> service.getAccessUrl("file-own"))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("无法生成文件访问地址");
    }

    @Test
    void shouldListPublicAttachments() {
        fileQueryRepository.attachments = List.of(new PublicAttachmentDescriptor(
                14001L,
                "FILE-0008",
                "综测申请模板",
                "学生申请材料填写模板",
                "INTELLECTUAL",
                "综测申请模板.pdf",
                "application/pdf",
                142000L,
                LocalDateTime.parse("2026-05-11T09:00:00"),
                10
        ));

        List<PublicAttachmentResponse> responses = service.listPublicAttachments("INTELLECTUAL");

        assertThat(fileQueryRepository.lastCategoryCode).isEqualTo("INTELLECTUAL");
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getEntryId()).isEqualTo(14001L);
        assertThat(responses.getFirst().getFileId()).isEqualTo("FILE-0008");
    }

    private static FileAssetDescriptor file(String fileId, String storageKey, Long uploaderUserId, String status) {
        return new FileAssetDescriptor(
                fileId,
                storageKey,
                fileId + ".pdf",
                "application/pdf",
                128L,
                uploaderUserId,
                status,
                LocalDateTime.parse("2026-07-06T10:00:00")
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
                    "2024305001",
                    "测试学生",
                    "STUDENT",
                    Set.of("STUDENT"),
                    Set.of(),
                    List.of()
            ));
        }
    }

    private static class StubFileQueryRepository implements FileQueryRepository {

        private FileAssetDescriptor file;
        private boolean publicVisible;
        private boolean applicationBoundVisible;
        private Long lastBoundUserId;
        private List<PublicAttachmentDescriptor> attachments = List.of();
        private String lastCategoryCode;

        @Override
        public Optional<FileAssetDescriptor> findActiveFileByFileId(String fileId) {
            return Optional.ofNullable(file);
        }

        @Override
        public boolean existsPublishedAllPublicAttachment(String fileId) {
            return publicVisible;
        }

        @Override
        public boolean existsVisibleApplicationBinding(String fileId,
                                                       edu.whut.eval.domain.application.query.ApplicationAccessContext accessContext) {
            lastBoundUserId = accessContext.getUserId();
            return applicationBoundVisible;
        }

        @Override
        public List<PublicAttachmentDescriptor> listPublishedAllPublicAttachments(String categoryCode) {
            lastCategoryCode = categoryCode;
            return attachments;
        }
    }
}
