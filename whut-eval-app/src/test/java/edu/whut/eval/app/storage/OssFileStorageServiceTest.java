package edu.whut.eval.app.storage;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.infra.nacos.config.OssStorageConfigProvider;
import edu.whut.eval.infra.nacos.model.typed.OssStorageConfig;
import edu.whut.eval.infra.storage.OssFileStorageService;
import edu.whut.eval.infra.storage.OssObjectStorageClient;
import edu.whut.eval.infra.storage.StoredOssObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OssFileStorageServiceTest {

    private final OssStorageConfigProvider ossStorageConfigProvider = mock(OssStorageConfigProvider.class);
    private final OssObjectStorageClient ossObjectStorageClient = mock(OssObjectStorageClient.class);

    private final OssFileStorageService fileStorageService =
            new OssFileStorageService(ossStorageConfigProvider, ossObjectStorageClient);

    @Test
    void shouldStoreFileAndReturnDescriptor() {
        OssStorageConfig config = enabledConfig();
        given(ossStorageConfigProvider.requiredConfig()).willReturn(config);
        given(ossObjectStorageClient.putObject(eq(config), any(String.class), any(), eq(5L), eq("image/png")))
                .willAnswer(invocation -> new StoredOssObject(
                        "whut-eval-dev",
                        invocation.getArgument(1, String.class),
                        "https://cdn.whut.example.com/" + invocation.getArgument(1, String.class)
                ));

        StoredFileDescriptor result = fileStorageService.store(new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "avatar.png",
                "image/png",
                "profile"
        ));

        assertThat(result.getBucket()).isEqualTo("whut-eval-dev");
        assertThat(result.getObjectKey()).startsWith("uploads/dev/profile/");
        assertThat(result.getObjectKey()).endsWith("-avatar.png");
        assertThat(result.getPublicUrl()).startsWith("https://cdn.whut.example.com/uploads/dev/profile/");
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getSize()).isEqualTo(5L);
    }

    @Test
    void shouldRejectWhenOssStorageIsDisabled() {
        OssStorageConfig config = enabledConfig();
        config.setEnabled(false);
        given(ossStorageConfigProvider.requiredConfig()).willReturn(config);

        assertThatThrownBy(() -> fileStorageService.store(new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "avatar.png",
                "image/png",
                "profile"
        ))).isInstanceOf(ValidationException.class)
                .hasMessage("OSS 文件存储当前未启用");
    }

    @Test
    void shouldNormalizeObjectKeySegmentsAndFilename() {
        OssStorageConfig config = enabledConfig();
        given(ossStorageConfigProvider.requiredConfig()).willReturn(config);
        given(ossObjectStorageClient.putObject(eq(config), any(String.class), any(), eq(5L), eq("image/png")))
                .willAnswer(invocation -> new StoredOssObject(
                        "whut-eval-dev",
                        invocation.getArgument(1, String.class),
                        null
                ));

        StoredFileDescriptor result = fileStorageService.store(new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "My Avatar (Final).PNG",
                "image/png",
                "/profile/avatar/"
        ));

        assertThat(result.getObjectKey()).startsWith("uploads/dev/profile/avatar/");
        assertThat(result.getObjectKey()).endsWith("-my_avatar__final_.png");
    }

    @Test
    void shouldPropagateContentTypeAndSizeToOssClient() {
        OssStorageConfig config = enabledConfig();
        given(ossStorageConfigProvider.requiredConfig()).willReturn(config);
        given(ossObjectStorageClient.putObject(eq(config), any(String.class), any(), eq(5L), eq("image/png")))
                .willReturn(new StoredOssObject("whut-eval-dev", "uploads/dev/profile/file.png", null));

        fileStorageService.store(new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "avatar.png",
                "image/png",
                "profile"
        ));

        verify(ossObjectStorageClient).putObject(eq(config), any(String.class), any(), eq(5L), eq("image/png"));
    }

    private OssStorageConfig enabledConfig() {
        OssStorageConfig config = new OssStorageConfig();
        config.setEnabled(true);
        config.setEndpoint("https://oss-cn-shanghai.aliyuncs.com");
        config.setRegion("cn-shanghai");
        config.setAccessKeyId("test-ak");
        config.setAccessKeySecret("test-sk");
        config.setBucket("whut-eval-dev");
        config.setPublicBaseUrl("https://cdn.whut.example.com");
        config.setKeyPrefix("uploads/dev");
        return config;
    }
}
