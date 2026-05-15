package edu.whut.eval.application.file.command;

import java.io.InputStream;

/**
 * 文件上传命令。
 * 该命令用于屏蔽 HTTP 层的 `MultipartFile` 细节，让 application 层只依赖最小上传参数。
 */
public class UploadFileCommand {

    private final InputStream inputStream;
    private final long size;
    private final String originalFilename;
    private final String contentType;
    private final String bizType;

    public UploadFileCommand(InputStream inputStream,
                             long size,
                             String originalFilename,
                             String contentType,
                             String bizType) {
        this.inputStream = inputStream;
        this.size = size;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bizType = bizType;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getSize() {
        return size;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getBizType() {
        return bizType;
    }
}
