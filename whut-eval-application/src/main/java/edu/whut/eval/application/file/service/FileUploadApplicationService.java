package edu.whut.eval.application.file.service;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import org.springframework.stereotype.Service;

/**
 * 文件上传应用服务。
 * 该服务用于承接接口层上传请求，保证写入型入口仍通过 application service 编排。
 */
@Service
public class FileUploadApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final FileStorageService fileStorageService;
    private final FileAssetRegistry fileAssetRegistry;

    public FileUploadApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                        FileStorageService fileStorageService,
                                        FileAssetRegistry fileAssetRegistry) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.fileStorageService = fileStorageService;
        this.fileAssetRegistry = fileAssetRegistry;
    }

    /**
     * 上传单个文件并返回标准化文件元信息。
     */
    public StoredFileDescriptor upload(UploadFileCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        StoredFileDescriptor storedFileDescriptor = fileStorageService.store(command);
        return fileAssetRegistry.registerUploadedFile(
                storedFileDescriptor,
                authorizationContext.getUserId(),
                authorizationContext.getIdentity()
        );
    }
}
