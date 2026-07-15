package edu.whut.eval.interfaces.file;

import edu.whut.eval.application.file.command.OfflinePublicAttachmentCommand;
import edu.whut.eval.application.file.command.PublishPublicAttachmentCommand;
import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.OfflinePublicAttachmentResult;
import edu.whut.eval.application.file.query.PublishPublicAttachmentResult;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.application.file.service.PublicAttachmentCommandApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.file.request.OfflinePublicAttachmentRequest;
import edu.whut.eval.interfaces.file.request.PublishPublicAttachmentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@PreAuthorize("isAuthenticated()")
public class FileQueryController {

    private final FileQueryApplicationService fileQueryApplicationService;
    private final PublicAttachmentCommandApplicationService publicAttachmentCommandApplicationService;

    public FileQueryController(FileQueryApplicationService fileQueryApplicationService,
                               PublicAttachmentCommandApplicationService publicAttachmentCommandApplicationService) {
        this.fileQueryApplicationService = fileQueryApplicationService;
        this.publicAttachmentCommandApplicationService = publicAttachmentCommandApplicationService;
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileMetadataResponse> getMetadata(@PathVariable String fileId) {
        return ApiResponse.success(fileQueryApplicationService.getMetadata(fileId));
    }

    @GetMapping("/{fileId}/access-url")
    public ApiResponse<FileAccessUrlResponse> getAccessUrl(@PathVariable String fileId,
                                                           @RequestParam(defaultValue = "inline") String disposition,
                                                           @RequestParam(defaultValue = "300") int expireSeconds) {
        return ApiResponse.success(fileQueryApplicationService.getAccessUrl(fileId, disposition, expireSeconds));
    }

    @GetMapping("/public-attachments")
    public ApiResponse<List<PublicAttachmentResponse>> listPublicAttachments(
            @RequestParam(required = false) String categoryCode) {
        return ApiResponse.success(fileQueryApplicationService.listPublicAttachments(categoryCode));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ATTACHMENT_POOL_PUBLISH)")
    @PostMapping("/public-attachments")
    public ApiResponse<PublishPublicAttachmentResult> publishPublicAttachment(
            @Valid @RequestBody PublishPublicAttachmentRequest request) {
        return ApiResponse.success(publicAttachmentCommandApplicationService.publish(new PublishPublicAttachmentCommand(
                request.getFileId(),
                request.getDisplayName(),
                request.getDescription(),
                request.getCategoryCode(),
                request.getScopeType(),
                request.getScopeValue(),
                request.getSortNo()
        )));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ATTACHMENT_POOL_OFFLINE)")
    @PatchMapping("/public-attachments/{entryId}/offline")
    public ApiResponse<OfflinePublicAttachmentResult> offlinePublicAttachment(@PathVariable Long entryId,
                                                                              @Valid @RequestBody OfflinePublicAttachmentRequest request) {
        return ApiResponse.success(publicAttachmentCommandApplicationService.offline(
                new OfflinePublicAttachmentCommand(entryId, request.getReason())
        ));
    }
}
