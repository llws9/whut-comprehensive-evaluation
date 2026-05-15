package edu.whut.eval.interfaces.file;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileUploadApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.interfaces.file.view.StoredFileDescriptorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 最小文件上传入口。
 * 该控制器只负责 HTTP 参数解析与返回组装，实际上传编排下沉到 application service。
 */
@RestController
@Validated
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileUploadApplicationService fileUploadApplicationService;

    public FileUploadController(FileUploadApplicationService fileUploadApplicationService) {
        this.fileUploadApplicationService = fileUploadApplicationService;
    }

    /**
     * 上传单个文件到对象存储。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoredFileDescriptorView> upload(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "bizType", required = false) String bizType)
            throws IOException {
        AppLog.info(log, "file.upload.request.received",
                "bizType", bizType,
                "originalFilename", file.getOriginalFilename(),
                "contentType", file.getContentType(),
                "size", file.getSize());
        if (file.isEmpty()) {
            AppLog.warn(log, "file.upload.request.rejected",
                    "reason", "empty-file",
                    "bizType", bizType,
                    "originalFilename", file.getOriginalFilename(),
                    "contentType", file.getContentType(),
                    "size", file.getSize());
            throw new ValidationException("上传文件不能为空");
        }
        try {
            StoredFileDescriptor descriptor = fileUploadApplicationService.upload(new UploadFileCommand(
                    file.getInputStream(),
                    file.getSize(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    bizType
            ));
            AppLog.info(log, "file.upload.request.completed",
                    "bizType", bizType,
                    "fileId", descriptor.getFileId(),
                    "originalFilename", descriptor.getOriginalFilename(),
                    "contentType", descriptor.getContentType(),
                    "size", descriptor.getSize(),
                    "bucket", descriptor.getBucket(),
                    "objectKey", descriptor.getObjectKey());
            return ApiResponse.success(toView(descriptor));
        } catch (IOException exception) {
            AppLog.error(log, exception, "file.upload.request.io-failed",
                    "bizType", bizType,
                    "originalFilename", file.getOriginalFilename(),
                    "contentType", file.getContentType(),
                    "size", file.getSize());
            throw exception;
        }
    }

    private StoredFileDescriptorView toView(StoredFileDescriptor descriptor) {
        return new StoredFileDescriptorView(
                descriptor.getFileId(),
                descriptor.getBucket(),
                descriptor.getObjectKey(),
                descriptor.getPublicUrl(),
                descriptor.getOriginalFilename(),
                descriptor.getContentType(),
                descriptor.getSize()
        );
    }
}
