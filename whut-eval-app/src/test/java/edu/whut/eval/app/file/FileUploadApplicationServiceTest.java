package edu.whut.eval.app.file;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileAssetRegistry;
import edu.whut.eval.application.file.service.FileStorageService;
import edu.whut.eval.application.file.service.FileUploadApplicationService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileUploadApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileAssetRegistry fileAssetRegistry = mock(FileAssetRegistry.class);

    private final FileUploadApplicationService fileUploadApplicationService =
            new FileUploadApplicationService(userAuthorizationContextAssembler, fileStorageService, fileAssetRegistry);

    @Test
    void shouldStoreFileThenRegisterFileAssetAndReturnFileId() {
        UploadFileCommand command = new UploadFileCommand(
                new ByteArrayInputStream("hello".getBytes()),
                5L,
                "avatar.png",
                "image/png",
                "profile"
        );
        StoredFileDescriptor storedDescriptor = new StoredFileDescriptor(
                "whut-eval-dev",
                "uploads/dev/profile/uuid-avatar.png",
                "https://cdn.whut.example.com/uploads/dev/profile/uuid-avatar.png",
                "avatar.png",
                "image/png",
                5L
        );
        StoredFileDescriptor registeredDescriptor = new StoredFileDescriptor(
                "file_01registered",
                "whut-eval-dev",
                "uploads/dev/profile/uuid-avatar.png",
                "https://cdn.whut.example.com/uploads/dev/profile/uuid-avatar.png",
                "avatar.png",
                "image/png",
                5L
        );
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(fileStorageService.store(any(UploadFileCommand.class))).willReturn(storedDescriptor);
        given(fileAssetRegistry.registerUploadedFile(storedDescriptor, 1001L, "student")).willReturn(registeredDescriptor);

        StoredFileDescriptor result = fileUploadApplicationService.upload(command);

        assertThat(result.getFileId()).isEqualTo("file_01registered");
        assertThat(result.getObjectKey()).isEqualTo("uploads/dev/profile/uuid-avatar.png");
        verify(fileStorageService).store(command);
        verify(fileAssetRegistry).registerUploadedFile(storedDescriptor, 1001L, "student");
    }

    private UserAuthorizationContext currentUser() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("attachment.upload.self"),
                List.of()
        );
    }
}
