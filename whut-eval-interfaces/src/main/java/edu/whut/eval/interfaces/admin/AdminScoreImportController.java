package edu.whut.eval.interfaces.admin;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.finalrecord.importing.ActivityImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.ImportActivitiesCommand;
import edu.whut.eval.application.finalrecord.importing.ImportLecturesCommand;
import edu.whut.eval.application.finalrecord.importing.ImportMentorScoresCommand;
import edu.whut.eval.application.finalrecord.importing.LectureImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportResult;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportResult;
import edu.whut.eval.interfaces.admin.response.ActivityImportFailedRowResponse;
import edu.whut.eval.interfaces.admin.response.ActivityImportResultResponse;
import edu.whut.eval.interfaces.admin.response.LectureImportFailedRowResponse;
import edu.whut.eval.interfaces.admin.response.LectureImportResultResponse;
import edu.whut.eval.interfaces.admin.response.MentorScoreImportFailedRowResponse;
import edu.whut.eval.interfaces.admin.response.MentorScoreImportResultResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@RestController
@Validated
@RequestMapping("/api/admin/imports")
public class AdminScoreImportController {

    private static final long MAX_LECTURE_IMPORT_BYTES = 5L * 1024 * 1024;
    private static final long MAX_ACTIVITY_IMPORT_BYTES = 5L * 1024 * 1024;
    private static final long MAX_MENTOR_SCORE_IMPORT_BYTES = 10L * 1024 * 1024;
    private static final DateTimeFormatter LECTURE_RESPONSE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final MentorScoreImportApplicationService importApplicationService;
    private final LectureImportApplicationService lectureImportApplicationService;
    private final ActivityImportApplicationService activityImportApplicationService;

    public AdminScoreImportController(MentorScoreImportApplicationService importApplicationService,
                                      LectureImportApplicationService lectureImportApplicationService,
                                      ActivityImportApplicationService activityImportApplicationService) {
        this.importApplicationService = importApplicationService;
        this.lectureImportApplicationService = lectureImportApplicationService;
        this.activityImportApplicationService = activityImportApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
    @PostMapping(value = "/mentor-scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MentorScoreImportResultResponse> importMentorScores(
            @RequestParam("file") MultipartFile file,
            @RequestParam("academicYear") String academicYear,
            @RequestParam(value = "importMode", defaultValue = "UPSERT") String importMode) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件不能为空");
        }
        if (importMode == null || importMode.isBlank()) {
            importMode = "UPSERT";
        }
        if (!"UPSERT".equals(importMode) && !"STRICT_INSERT".equals(importMode)) {
            throw new ValidationException("importMode 仅允许 UPSERT 或 STRICT_INSERT");
        }
        if (file.getSize() > MAX_MENTOR_SCORE_IMPORT_BYTES) {
            throw new ValidationException("导师评分导入文件不超过 10MB");
        }
        validateWorkbookFilename(file, "导师评分");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("文件处理失败，请稍后重试", exception);
        }

        MentorScoreImportResult result = importApplicationService.importMentorScores(
                new ImportMentorScoresCommand(bytes, academicYear, importMode)
        );
        return ApiResponse.success(toResponse(result));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
    @PostMapping(value = "/lectures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LectureImportResultResponse> importLectures(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "heldAt", required = false) String heldAt,
            @RequestParam(value = "academicYear", required = false) String academicYear) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new ValidationException("title 不能为空");
        }
        if (heldAt == null || heldAt.isBlank()) {
            throw new ValidationException("heldAt 格式非法");
        }
        if (academicYear == null || academicYear.isBlank()) {
            throw new ValidationException("academicYear 不合法");
        }
        if (file.getSize() > MAX_LECTURE_IMPORT_BYTES) {
            throw new ValidationException("讲座导入文件最多支持 5000 行且不超过 5MB");
        }
        validateLectureWorkbookFilename(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("文件处理失败，请稍后重试", exception);
        }

        LectureImportResult result = lectureImportApplicationService.importLectures(
                new ImportLecturesCommand(bytes, title, heldAt, academicYear)
        );
        return ApiResponse.success(toResponse(result));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
    @PostMapping(value = "/cas-activities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ActivityImportResultResponse> importActivities(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "itemCode", required = false) String itemCode,
            @RequestParam(value = "scoreValue", required = false) String scoreValue,
            @RequestParam(value = "heldAt", required = false) String heldAt,
            @RequestParam(value = "academicYear", required = false) String academicYear) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件不能为空");
        }
        if (file.getSize() > MAX_ACTIVITY_IMPORT_BYTES) {
            throw new ValidationException("文体活动导入文件最多支持 5000 行且不超过 5MB");
        }
        validateActivityWorkbookFilename(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("文件处理失败，请稍后重试", exception);
        }

        ActivityImportResult result = activityImportApplicationService.importActivities(
                new ImportActivitiesCommand(bytes, title, itemCode, scoreValue, heldAt, academicYear)
        );
        return ApiResponse.success(toResponse(result));
    }

    private void validateLectureWorkbookFilename(MultipartFile file) {
        validateWorkbookFilename(file, "讲座");
    }

    private void validateActivityWorkbookFilename(MultipartFile file) {
        validateWorkbookFilename(file, "文体活动");
    }

    private void validateWorkbookFilename(MultipartFile file, String label) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".xlsx") && !normalized.endsWith(".xls")) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
    }

    private MentorScoreImportResultResponse toResponse(MentorScoreImportResult result) {
        return new MentorScoreImportResultResponse(
                result.importBatchId(),
                result.totalCount(),
                result.successCount(),
                result.failedCount(),
                toFailedRowResponses(result.failedRows()),
                result.processedAt().toString()
        );
    }

    private List<MentorScoreImportFailedRowResponse> toFailedRowResponses(List<MentorScoreImportFailedRow> failedRows) {
        return failedRows.stream()
                .map(row -> new MentorScoreImportFailedRowResponse(row.rowNo(), row.code(), row.message(), row.rawValue()))
                .toList();
    }

    private LectureImportResultResponse toResponse(LectureImportResult result) {
        return new LectureImportResultResponse(
                result.lectureBatchId(),
                result.title(),
                result.heldAt().format(LECTURE_RESPONSE_TIME_FORMATTER),
                result.academicYear(),
                result.totalCount(),
                result.successCount(),
                result.failedCount(),
                toLectureFailedRowResponses(result.failedRows())
        );
    }

    private List<LectureImportFailedRowResponse> toLectureFailedRowResponses(List<LectureImportFailedRow> failedRows) {
        return failedRows.stream()
                .map(row -> new LectureImportFailedRowResponse(row.rowNo(), row.code(), row.message(), row.rawValue()))
                .toList();
    }

    private ActivityImportResultResponse toResponse(ActivityImportResult result) {
        return new ActivityImportResultResponse(
                result.activityBatchId(),
                result.title(),
                result.itemCode(),
                result.scoreValue(),
                result.totalCount(),
                result.successCount(),
                result.failedCount(),
                toActivityFailedRowResponses(result.failedRows())
        );
    }

    private List<ActivityImportFailedRowResponse> toActivityFailedRowResponses(List<ActivityImportFailedRow> failedRows) {
        return failedRows.stream()
                .map(row -> new ActivityImportFailedRowResponse(row.rowNo(), row.code(), row.message(), row.rawValue()))
                .toList();
    }
}
