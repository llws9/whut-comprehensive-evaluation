package edu.whut.eval.app.storage;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.infra.nacos.model.typed.OssStorageConfig;
import edu.whut.eval.infra.storage.OssFileStorageService;
import edu.whut.eval.infra.storage.OssObjectStorageClient;
import edu.whut.eval.infra.storage.StoredOssObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssFileStorageServiceTest {

    private StubOssStorageConfigProvider ossStorageConfigProvider;
    private StubOssObjectStorageClient ossObjectStorageClient;
    private OssFileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        ossStorageConfigProvider = new StubOssStorageConfigProvider();
        ossObjectStorageClient = new StubOssObjectStorageClient();
        fileStorageService = new OssFileStorageService(ossStorageConfigProvider, ossObjectStorageClient);
        ossStorageConfigProvider.setConfig(enabledConfig());
    }

    @Test
    void shouldStoreFileAndReturnDescriptor() {
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
        ossStorageConfigProvider.setConfig(config);

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
        fileStorageService.store(new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "avatar.png",
                "image/png",
                "profile"
        ));

        assertThat(ossObjectStorageClient.lastContentType).isEqualTo("image/png");
        assertThat(ossObjectStorageClient.lastSize).isEqualTo(5L);
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

    private static class StubOssStorageConfigProvider extends edu.whut.eval.infra.nacos.config.OssStorageConfigProvider {
        private OssStorageConfig config;

        StubOssStorageConfigProvider() {
            super(null);
        }

        void setConfig(OssStorageConfig config) {
            this.config = config;
        }

        @Override
        public Optional<OssStorageConfig> currentConfig() {
            return Optional.ofNullable(config);
        }

        @Override
        public OssStorageConfig requiredConfig() {
            return currentConfig().orElseThrow(() -> new ValidationException("OSS 文件存储当前未启用"));
        }
    }

    private static class StubOssObjectStorageClient implements OssObjectStorageClient {
        String lastContentType;
        long lastSize;

        @Override
        public StoredOssObject putObject(OssStorageConfig config, String objectKey, InputStream inputStream, long contentLength, String contentType) {
            this.lastContentType = contentType;
            this.lastSize = contentLength;
            String publicUrl = config.getPublicBaseUrl() != null ? config.getPublicBaseUrl() + "/" + objectKey : null;
            return new StoredOssObject(config.getBucket(), objectKey, publicUrl);
        }
    }
}