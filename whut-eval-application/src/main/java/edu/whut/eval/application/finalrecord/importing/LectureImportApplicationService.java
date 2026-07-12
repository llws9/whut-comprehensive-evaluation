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
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LectureImportApplicationService {

    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern STRICT_DECIMAL_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    private static final BigDecimal MAX_SCORE = new BigDecimal("99999999.99");
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

        return transactionOperations.execute(status -> importPreparedRows(request, lectureBatchId, context, preparedRows));
    }

    private LectureImportResult importPreparedRows(NormalizedRequest request,
                                                   String lectureBatchId,
                                                   UserAuthorizationContext context,
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
            return processRows(request, lectureBatchId, context, preparedRows);
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
                                            UserAuthorizationContext context,
                                            PreparedLectureRows preparedRows) {
        List<LectureImportFailedRow> failedRows = new ArrayList<>(preparedRows.failedRows());
        List<ResolvedLectureRow> resolvedRows = new ArrayList<>();
        Map<Long, Optional<String>> orgPathCache = new HashMap<>();

        for (FieldValidLectureRow candidate : preparedRows.fieldValidRows()) {
            LectureImportRow row = candidate.row();
            Optional<LectureImportStudentTarget> target = repository.findTarget(candidate.studentNo(), request.academicYear());
            if (target.isEmpty()) {
                failedRows.add(failed(row, "STUDENT_NOT_FOUND", "studentNo 对应学生不存在或未启用"));
                continue;
            }
            if (!canAccess(context, target.get(), orgPathCache)) {
                failedRows.add(failed(row, "OUT_OF_SCOPE", "当前用户无权导入该学生讲座成绩"));
                continue;
            }

            String rawDisplayText = row.displayText();
            String displayText = isBlank(rawDisplayText) ? request.title() + " 讲座签到" : rawDisplayText.trim();
            resolvedRows.add(new ResolvedLectureRow(
                    row.rowNo(),
                    target.get().studentUserId(),
                    candidate.studentNo(),
                    candidate.scoreValueText(),
                    candidate.scoreValue(),
                    rawDisplayText,
                    displayText
            ));
        }

        resolvedRows.sort(Comparator.comparing(ResolvedLectureRow::studentUserId).thenComparing(ResolvedLectureRow::rowNo));
        List<LectureImportedComponent> components = resolvedRows.stream()
                .map(row -> new LectureImportedComponent(
                        row.rowNo(),
                        row.studentUserId(),
                        row.studentNo(),
                        row.scoreValueText(),
                        row.scoreValue(),
                        row.rawDisplayText(),
                        row.displayText()
                ))
                .toList();
        if (!components.isEmpty()) {
            failedRows.addAll(repository.insertLectureComponents(request.academicYear(), lectureBatchId, components));
        }

        failedRows.sort(Comparator.comparing(LectureImportFailedRow::rowNo));
        long totalCount = preparedRows.totalCount();
        long failedCount = failedRows.size();
        return new LectureImportResult(
                lectureBatchId,
                request.title(),
                request.heldAt(),
                request.academicYear(),
                totalCount,
                totalCount - failedCount,
                failedCount,
                List.copyOf(failedRows)
        );
    }

    private PreparedLectureRows prepareRows(List<LectureImportRow> rows) {
        List<LectureImportFailedRow> failedRows = new ArrayList<>();
        List<FieldValidLectureRow> fieldValidRows = new ArrayList<>();
        Set<String> seenStudentNos = new LinkedHashSet<>();

        for (LectureImportRow row : rows) {
            Optional<LectureImportFailedRow> fieldFailure = validateFields(row);
            if (fieldFailure.isPresent()) {
                failedRows.add(fieldFailure.get());
                continue;
            }

            String studentNo = row.studentNo().trim();
            if (!seenStudentNos.add(studentNo)) {
                failedRows.add(failed(row, "DUPLICATE_STUDENT", "同一讲座批次中学生重复"));
                continue;
            }
            BigDecimal score = new BigDecimal(row.scoreValue().trim()).setScale(2, RoundingMode.HALF_UP);
            fieldValidRows.add(new FieldValidLectureRow(row, studentNo, row.scoreValue().trim(), score));
        }

        return new PreparedLectureRows(rows.size(), failedRows, fieldValidRows);
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

    private Optional<LectureImportFailedRow> validateFields(LectureImportRow row) {
        if (isBlank(row.studentNo())) {
            return Optional.of(failed(row, "STUDENT_NO_REQUIRED", "studentNo 不能为空"));
        }
        if (isBlank(row.scoreValue())) {
            return Optional.of(failed(row, "SCORE_VALUE_REQUIRED", "scoreValue 不能为空"));
        }
        String value = row.scoreValue().trim();
        if (!STRICT_DECIMAL_PATTERN.matcher(value).matches()) {
            return Optional.of(failed(row, "SCORE_VALUE_INVALID", "scoreValue 必须是数字"));
        }
        BigDecimal score = new BigDecimal(value);
        if (score.compareTo(MAX_SCORE) > 0) {
            return Optional.of(failed(row, "SCORE_VALUE_OUT_OF_RANGE", "scoreValue 必须在 0 到 99999999.99 之间"));
        }
        if (score.scale() > 2) {
            return Optional.of(failed(row, "SCORE_VALUE_SCALE_INVALID", "scoreValue 最多保留 2 位小数"));
        }
        if (row.displayText() != null && row.displayText().codePointCount(0, row.displayText().length()) > 1000) {
            return Optional.of(failed(row, "DISPLAY_TEXT_TOO_LONG", "displayText 长度不能超过 1000"));
        }
        return Optional.empty();
    }

    private LectureImportFailedRow failed(LectureImportRow row, String code, String message) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("studentNo", row.studentNo());
        raw.put("scoreValue", row.scoreValue());
        raw.put("displayText", row.displayText());
        return new LectureImportFailedRow(row.rowNo(), code, message, raw);
    }

    private boolean canAccess(UserAuthorizationContext context,
                              LectureImportStudentTarget target,
                              Map<Long, Optional<String>> orgPathCache) {
        for (IamScopeRule rule : context.findScopeRulesByPermissionCode(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            if (!"ACTIVE".equals(rule.status())) {
                continue;
            }
            if ("ALL".equals(rule.scopeType())) {
                return true;
            }
            if ("ORG_UNIT".equals(rule.scopeType()) && rule.orgUnitId() != null && rule.orgUnitId().equals(target.orgUnitId())) {
                return true;
            }
            if ("ORG_SUBTREE".equals(rule.scopeType()) && matchesOrgSubtree(rule.orgUnitId(), target.orgPath(), orgPathCache)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOrgSubtree(Long rootOrgUnitId,
                                      String targetPath,
                                      Map<Long, Optional<String>> orgPathCache) {
        if (rootOrgUnitId == null || isBlank(targetPath)) {
            return false;
        }
        Optional<String> rootPath = orgPathCache.computeIfAbsent(rootOrgUnitId, repository::findActiveOrgPath);
        if (rootPath.isEmpty() || isBlank(rootPath.get())) {
            return false;
        }
        String normalizedRootPath = trimTrailingSlash(rootPath.get().trim());
        String normalizedTargetPath = trimTrailingSlash(targetPath.trim());
        return normalizedTargetPath.equals(normalizedRootPath)
                || normalizedTargetPath.startsWith(normalizedRootPath + "/");
    }

    private String trimTrailingSlash(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record NormalizedRequest(String title, LocalDateTime heldAt, String academicYear) {
    }

    private record PreparedLectureRows(
            long totalCount,
            List<LectureImportFailedRow> failedRows,
            List<FieldValidLectureRow> fieldValidRows
    ) {
    }

    private record FieldValidLectureRow(
            LectureImportRow row,
            String studentNo,
            String scoreValueText,
            BigDecimal scoreValue
    ) {
    }

    private record ResolvedLectureRow(
            Long rowNo,
            Long studentUserId,
            String studentNo,
            String scoreValueText,
            BigDecimal scoreValue,
            String rawDisplayText,
            String displayText
    ) {
    }
}
