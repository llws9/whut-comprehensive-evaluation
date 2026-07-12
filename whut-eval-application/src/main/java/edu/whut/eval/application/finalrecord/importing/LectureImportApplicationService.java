package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LectureImportApplicationService {

    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Duration BATCH_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final UserAuthorizationContextAssembler authorizationContextAssembler;
    private final LectureImportParser parser;
    private final LectureImportRepository repository;
    private final LectureImportBatchLock batchLock;
    private final TransactionOperations transactionOperations;

    public LectureImportApplicationService(UserAuthorizationContextAssembler authorizationContextAssembler,
                                           LectureImportParser parser,
                                           LectureImportRepository repository,
                                           LectureImportBatchLock batchLock,
                                           TransactionOperations transactionOperations) {
        this.authorizationContextAssembler = authorizationContextAssembler;
        this.parser = parser;
        this.repository = repository;
        this.batchLock = batchLock;
        this.transactionOperations = transactionOperations;
    }

    public LectureImportResult importLectures(ImportLecturesCommand command) {
        NormalizedRequest request = normalize(command);
        UserAuthorizationContext context = authorizationContextAssembler.requiredAuthorizationContext();
        if (!context.hasAuthority(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            throw new AccessDeniedAppException("当前用户无导入权限");
        }
        String lectureBatchId = lectureBatchId(request);
        List<LectureImportRow> rows = parser.parse(command.fileContent());
        PreparedLectureRows preparedRows = prepareRows(rows);

        return transactionOperations.execute(status -> importPreparedRows(request, lectureBatchId, preparedRows));
    }

    private LectureImportResult importPreparedRows(NormalizedRequest request,
                                                   String lectureBatchId,
                                                   PreparedLectureRows preparedRows) {
        boolean acquired = batchLock.tryAcquire(lectureBatchId, BATCH_LOCK_TIMEOUT);
        if (!acquired) {
            throw new ConflictException("同一讲座批次正在导入，请稍后重试");
        }
        boolean releaseRegistered = false;
        try {
            releaseRegistered = registerBatchLockRelease(lectureBatchId);
            if (repository.lectureBatchExists(request.academicYear(), lectureBatchId)) {
                throw new ConflictException("同一讲座批次已导入");
            }
            return processRows(request, lectureBatchId, preparedRows);
        } finally {
            if (!releaseRegistered) {
                batchLock.release(lectureBatchId);
            }
        }
    }

    private boolean registerBatchLockRelease(String lectureBatchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                batchLock.release(lectureBatchId);
            }
        });
        return true;
    }

    private LectureImportResult processRows(NormalizedRequest request,
                                            String lectureBatchId,
                                            PreparedLectureRows preparedRows) {
        return new LectureImportResult(
                lectureBatchId,
                request.title(),
                request.heldAt(),
                request.academicYear(),
                preparedRows.totalCount(),
                0,
                preparedRows.failedRows().size(),
                preparedRows.failedRows()
        );
    }

    private PreparedLectureRows prepareRows(List<LectureImportRow> rows) {
        return new PreparedLectureRows(rows.size(), List.of(), rows);
    }

    private NormalizedRequest normalize(ImportLecturesCommand command) {
        if (command.fileContent() == null || command.fileContent().length == 0) {
            throw new ValidationException("上传文件不能为空");
        }
        String title = normalizeTitle(command.title());
        LocalDateTime heldAt = normalizeHeldAt(command.heldAt());
        String academicYear = normalizeAcademicYear(command.academicYear());
        return new NormalizedRequest(title, heldAt, academicYear);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("title 不能为空");
        }
        String value = title.trim();
        if (value.codePointCount(0, value.length()) > 255) {
            throw new ValidationException("title 长度不能超过 255");
        }
        return value;
    }

    private LocalDateTime normalizeHeldAt(String heldAt) {
        if (heldAt == null || heldAt.isBlank()) {
            throw new ValidationException("heldAt 格式非法");
        }
        try {
            return LocalDateTime.parse(heldAt.trim()).withNano(0);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("heldAt 格式非法");
        }
    }

    private String normalizeAcademicYear(String academicYear) {
        if (academicYear == null) {
            throw new ValidationException("academicYear 不合法");
        }
        String value = academicYear.trim();
        Matcher matcher = ACADEMIC_YEAR_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ValidationException("academicYear 不合法");
        }
        int start = Integer.parseInt(matcher.group(1));
        int end = Integer.parseInt(matcher.group(2));
        if (end != start + 1) {
            throw new ValidationException("academicYear 不合法");
        }
        return value;
    }

    private String lectureBatchId(NormalizedRequest request) {
        String year = request.academicYear().replace("-", "");
        String held = request.heldAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String hashInput = request.academicYear() + "|" + held + "|" + request.title();
        return "LECTURE-" + year + "-" + held + "-" + sha256Prefix(hashInput);
    }

    private String sha256Prefix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.substring(0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record NormalizedRequest(String title, LocalDateTime heldAt, String academicYear) {
    }

    private record PreparedLectureRows(long totalCount,
                                       List<LectureImportFailedRow> failedRows,
                                       List<LectureImportRow> fieldValidRows) {
    }
}
