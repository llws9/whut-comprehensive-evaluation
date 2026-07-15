package edu.whut.eval.app.file;

import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.OfflinePublicAttachmentResult;
import edu.whut.eval.application.file.query.PublishPublicAttachmentResult;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.application.file.service.PublicAttachmentCommandApplicationService;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.file.FileQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileQueryController.class)
@AutoConfigureMockMvc
@ContextConfiguration(classes = FileQueryControllerWebMvcTest.TestApplication.class)
@Import({
        FileQueryController.class,
        GlobalExceptionHandler.class,
        FileQueryControllerWebMvcTest.TestBeans.class
})
class FileQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubFileQueryApplicationService fileQueryApplicationService;

    @Autowired
    private StubPublicAttachmentCommandApplicationService publicAttachmentCommandApplicationService;

    @Test
    void shouldReturnMetadataShapeWithoutStorageFields() throws Exception {
        fileQueryApplicationService.metadata = new FileMetadataResponse(
                "file-own",
                "award.pdf",
                "application/pdf",
                128L,
                "ACTIVE",
                "SELF_UPLOAD",
                true,
                true,
                LocalDateTime.parse("2026-07-06T10:00:00")
        );

        mockMvc.perform(get("/api/files/file-own")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value("file-own"))
                .andExpect(jsonPath("$.data.originalFilename").value("award.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.size").value(128))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sourceType").value("SELF_UPLOAD"))
                .andExpect(jsonPath("$.data.canPreview").value(true))
                .andExpect(jsonPath("$.data.canDownload").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.bucket").doesNotExist())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.uploaderUserId").doesNotExist());
    }

    @Test
    void shouldReturnAccessUrlShape() throws Exception {
        fileQueryApplicationService.accessUrl = new FileAccessUrlResponse(
                "file-own",
                "https://cdn.example.com/uploads/own.pdf",
                "PUBLIC_URL",
                null
        );

        mockMvc.perform(get("/api/files/file-own/access-url")
                        .param("disposition", "attachment")
                        .param("expireSeconds", "600")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value("file-own"))
                .andExpect(jsonPath("$.data.accessUrl").value("https://cdn.example.com/uploads/own.pdf"))
                .andExpect(jsonPath("$.data.accessMode").value("PUBLIC_URL"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(fileQueryApplicationService.lastDisposition).isEqualTo("attachment");
        org.assertj.core.api.Assertions.assertThat(fileQueryApplicationService.lastExpireSeconds).isEqualTo(600);
    }

    @Test
    void shouldReturnFlatPublicAttachmentList() throws Exception {
        fileQueryApplicationService.publicAttachments = List.of(new PublicAttachmentResponse(
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

        mockMvc.perform(get("/api/files/public-attachments")
                        .param("categoryCode", "INTELLECTUAL")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].entryId").value(14001))
                .andExpect(jsonPath("$.data[0].fileId").value("FILE-0008"))
                .andExpect(jsonPath("$.data[0].displayName").value("综测申请模板"))
                .andExpect(jsonPath("$.data[0].categoryCode").value("INTELLECTUAL"))
                .andExpect(jsonPath("$.data[0].originalFilename").value("综测申请模板.pdf"));
    }

    @Test
    void shouldPublishPublicAttachment() throws Exception {
        publicAttachmentCommandApplicationService.publishResult = new PublishPublicAttachmentResult(
                15001L,
                "FILE-NEW",
                "PUBLISHED",
                "ALL",
                LocalDateTime.parse("2026-07-15T10:00:00")
        );

        mockMvc.perform(post("/api/files/public-attachments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fileId": "FILE-NEW",
                                  "displayName": "综测模板",
                                  "description": "学生填写说明",
                                  "categoryCode": "INTELLECTUAL",
                                  "scopeType": "ALL",
                                  "sortNo": 10
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "attachment.pool.publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entryId").value(15001))
                .andExpect(jsonPath("$.data.fileId").value("FILE-NEW"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.scopeType").value("ALL"))
                .andExpect(jsonPath("$.data.publishedAt").exists());
        org.assertj.core.api.Assertions.assertThat(publicAttachmentCommandApplicationService.lastPublishCommand.fileId())
                .isEqualTo("FILE-NEW");
        org.assertj.core.api.Assertions.assertThat(publicAttachmentCommandApplicationService.lastPublishCommand.displayName())
                .isEqualTo("综测模板");
    }

    @Test
    void shouldRejectPublishWithoutAuthority() throws Exception {
        mockMvc.perform(post("/api/files/public-attachments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fileId": "FILE-NEW",
                                  "displayName": "综测模板",
                                  "scopeType": "ALL"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldOfflinePublicAttachment() throws Exception {
        publicAttachmentCommandApplicationService.offlineResult = new OfflinePublicAttachmentResult(
                15001L,
                "OFFLINE",
                LocalDateTime.parse("2026-07-15T11:00:00")
        );

        mockMvc.perform(patch("/api/files/public-attachments/15001/offline")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "资料过期"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "attachment.pool.offline")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entryId").value(15001))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.offlineAt").exists());
        org.assertj.core.api.Assertions.assertThat(publicAttachmentCommandApplicationService.lastOfflineCommand.entryId())
                .isEqualTo(15001L);
        org.assertj.core.api.Assertions.assertThat(publicAttachmentCommandApplicationService.lastOfflineCommand.reason())
                .isEqualTo("资料过期");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        StubFileQueryApplicationService fileQueryApplicationService() {
            return new StubFileQueryApplicationService();
        }

        @Bean
        StubPublicAttachmentCommandApplicationService publicAttachmentCommandApplicationService() {
            return new StubPublicAttachmentCommandApplicationService();
        }
    }

    static class StubFileQueryApplicationService extends FileQueryApplicationService {

        private FileMetadataResponse metadata;
        private FileAccessUrlResponse accessUrl;
        private List<PublicAttachmentResponse> publicAttachments = List.of();
        private String lastDisposition;
        private int lastExpireSeconds;

        StubFileQueryApplicationService() {
            super(null, null, null);
        }

        @Override
        public FileMetadataResponse getMetadata(String fileId) {
            return metadata;
        }

        @Override
        public FileAccessUrlResponse getAccessUrl(String fileId) {
            return accessUrl;
        }

        @Override
        public FileAccessUrlResponse getAccessUrl(String fileId, String disposition, int expireSeconds) {
            lastDisposition = disposition;
            lastExpireSeconds = expireSeconds;
            return accessUrl;
        }

        @Override
        public List<PublicAttachmentResponse> listPublicAttachments(String categoryCode) {
            return publicAttachments;
        }
    }

    static class StubPublicAttachmentCommandApplicationService extends PublicAttachmentCommandApplicationService {

        private PublishPublicAttachmentResult publishResult;
        private OfflinePublicAttachmentResult offlineResult;
        private edu.whut.eval.application.file.command.PublishPublicAttachmentCommand lastPublishCommand;
        private edu.whut.eval.application.file.command.OfflinePublicAttachmentCommand lastOfflineCommand;

        StubPublicAttachmentCommandApplicationService() {
            super(null, null);
        }

        @Override
        public PublishPublicAttachmentResult publish(edu.whut.eval.application.file.command.PublishPublicAttachmentCommand command) {
            lastPublishCommand = command;
            return publishResult;
        }

        @Override
        public OfflinePublicAttachmentResult offline(edu.whut.eval.application.file.command.OfflinePublicAttachmentCommand command) {
            lastOfflineCommand = command;
            return offlineResult;
        }
    }
}
