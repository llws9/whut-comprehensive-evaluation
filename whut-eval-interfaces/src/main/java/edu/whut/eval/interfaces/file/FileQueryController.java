package edu.whut.eval.interfaces.file;

import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@PreAuthorize("isAuthenticated()")
public class FileQueryController {

    private final FileQueryApplicationService fileQueryApplicationService;

    public FileQueryController(FileQueryApplicationService fileQueryApplicationService) {
        this.fileQueryApplicationService = fileQueryApplicationService;
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileMetadataResponse> getMetadata(@PathVariable String fileId) {
        return ApiResponse.success(fileQueryApplicationService.getMetadata(fileId));
    }

    @GetMapping("/{fileId}/access-url")
    public ApiResponse<FileAccessUrlResponse> getAccessUrl(@PathVariable String fileId) {
        return ApiResponse.success(fileQueryApplicationService.getAccessUrl(fileId));
    }

    @GetMapping("/public-attachments")
    public ApiResponse<List<PublicAttachmentResponse>> listPublicAttachments(
            @RequestParam(required = false) String categoryCode) {
        return ApiResponse.success(fileQueryApplicationService.listPublicAttachments(categoryCode));
    }
}
