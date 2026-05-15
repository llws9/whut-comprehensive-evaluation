package edu.whut.eval.app.file;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileUploadApplicationService;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.file.FileUploadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = FileUploadControllerWebMvcTest.TestApplication.class)
@Import({
        FileUploadController.class,
        GlobalExceptionHandler.class
})
class FileUploadControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileUploadApplicationService fileUploadApplicationService;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "hello".getBytes());
        given(fileUploadApplicationService.upload(any(UploadFileCommand.class)))
                .willReturn(new StoredFileDescriptor(
                        "file_01testupload",
                        "whut-eval-dev",
                        "uploads/dev/profile/20260514/uuid-avatar.png",
                        "https://cdn.whut.example.com/uploads/dev/profile/20260514/uuid-avatar.png",
                        "avatar.png",
                        "image/png",
                        5L
                ));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("bizType", "profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value("file_01testupload"))
                .andExpect(jsonPath("$.data.bucket").value("whut-eval-dev"))
                .andExpect(jsonPath("$.data.objectKey").value("uploads/dev/profile/20260514/uuid-avatar.png"))
                .andExpect(jsonPath("$.data.publicUrl").value("https://cdn.whut.example.com/uploads/dev/profile/20260514/uuid-avatar.png"))
                .andExpect(jsonPath("$.data.originalFilename").value("avatar.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(fileUploadApplicationService).upload(argThat(command ->
                command.getInputStream() != null
                        && command.getSize() == 5L
                        && "avatar.png".equals(command.getOriginalFilename())
                        && "image/png".equals(command.getContentType())
                        && "profile".equals(command.getBizType())
        ));
    }

    @Test
    void shouldReturn400WhenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(emptyFile)
                        .param("bizType", "profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
    }

    @Test
    void shouldReturn503WhenUploadFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "hello".getBytes());
        given(fileUploadApplicationService.upload(any(UploadFileCommand.class)))
                .willThrow(new FileStorageException("上传文件到 OSS 失败"));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("bizType", "profile"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("上传文件到 OSS 失败"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
