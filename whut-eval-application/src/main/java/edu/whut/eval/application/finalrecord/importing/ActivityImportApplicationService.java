package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportResult;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
public class ActivityImportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ActivityImportApplicationService.class);
    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern STRICT_DECIMAL_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    private static final BigDecimal MAX_SCORE = new BigDecimal("99999999.99");
    private static final Duration BATCH_LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter BATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserAuthorizationContextAssembler authorizationContextAssembler;
    private final ActivityImportParser parser;
    private final ActivityImportRepository repository;
    private final ActivityImportBatchLock batchLock;
    private final TransactionOperations transactionOperations;

    public ActivityImportApplicationService(UserAuthorizationContextAssembler authorizationContextAssembler,
                                            ActivityImportParser parser,
                                            ActivityImportRepository repository,
                                            ActivityImportBatchLock batchLock,
                                            TransactionOperations transactionOperations) {
        this.authorizationContextAssembler = authorizationContextAssembler;
        this.parser = parser;
        this.repository = repository;
        this.batchLock = batchLock;
        this.transactionOperations = transactionOperations;
    }

    public ActivityImportResult importActivities(ImportActivitiesCommand command) {
        NormalizedRequest request = normalize(command);
        UserAuthorizationContext context = authorizationContextAssembler.requiredAuthorizationContext();
        if (!context.hasAuthority(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            throw new AccessDeniedAppException("当前用户无导入权限");
        }

        List<ActivityImportRow> rows = parser.parse(command.fileContent());
        ActivityImportItemDefinition item = repository.findActiveSportsItem(request.itemCode())
                .orElseThrow(() -> new ResourceNotFoundException("对应项目定义不存在"));
        if (!item.allowOverflow() && request.scoreValue().compareTo(item.maxPoints()) > 0) {
            throw new ValidationException("scoreValue 必须在 0 到项目允许范围之间");
        }
        NormalizedRequest canonicalRequest = new NormalizedRequest(
                request.title(),
                item.itemCode(),
                request.scoreValue(),
                request.heldAt(),
                request.academicYear()
        );
        String activityBatchId = activityBatchId(canonicalRequest);
        PreparedActivityRows preparedRows = prepareRows(rows);

        return transactionOperations.execute(status -> importPreparedRows(canonicalRequest, item, activityBatchId, context, preparedRows));
    }

    private ActivityImportResult importPreparedRows(NormalizedRequest request,
                                                    ActivityImportItemDefinition item,
                                                    String activityBatchId,
                                                    UserAuthorizationContext context,
                                                    PreparedActivityRows preparedRows) {
        boolean acquired = batchLock.tryAcquire(activityBatchId, BATCH_LOCK_TIMEOUT);
        if (!acquired) {
            throw new ConflictException("同一活动批次正在导入，请稍后重试");
        }
        boolean releaseRegistered = false;
        try {
            releaseRegistered = registerBatchLockRelease(activityBatchId);
            if (repository.activityBatchExists(request.academicYear(), "SPORTS", item.itemCode(), activityBatchId)) {
                throw new ConflictException("同一活动批次已导入");
            }
            return processRows(request, item, activityBatchId, context, preparedRows);
        } finally {
            if (!releaseRegistered) {
                batchLock.release(activityBatchId);
            }
        }
    }

    private boolean registerBatchLockRelease(String activityBatchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    batchLock.release(activityBatchId);
                } catch (DataAccessException exception) {
                    log.warn("Failed to release activity import batch lock after transaction completion: {}, error={}",
                            activityBatchId,
                            exception.toString());
                }
            }
        });
        return true;
    }

    private ActivityImportResult processRows(NormalizedRequest request,
                                             ActivityImportItemDefinition item,
                                             String activityBatchId,
                                             UserAuthorizationContext context,
                                             PreparedActivityRows preparedRows) {
        List<ActivityImportFailedRow> failedRows = new ArrayList<>(preparedRows.failedRows());
        List<ResolvedActivityRow> resolvedRows = new ArrayList<>();
        Map<Long, Optional<String>> orgPathCache = new HashMap<>();

        for (FieldValidActivityRow candidate : preparedRows.fieldValidRows()) {
            ActivityImportRow row = candidate.row();
            Optional<ActivityImportStudentTarget> target = repository.findTarget(candidate.studentNo(), request.academicYear());
            if (target.isEmpty()) {
                failedRows.add(failed(row, "STUDENT_NOT_FOUND", "studentNo 对应学生不存在或未启用"));
                continue;
            }
            if (!canAccess(context, target.get(), orgPathCache)) {
                failedRows.add(failed(row, "OUT_OF_SCOPE", "当前用户无权导入该学生文体活动成绩"));
                continue;
            }

            String rawDisplayText = row.displayText();
            String displayText = isBlank(rawDisplayText) ? request.title() : rawDisplayText.trim();
            resolvedRows.add(new ResolvedActivityRow(
                    row.rowNo(),
                    target.get().studentUserId(),
                    candidate.studentNo(),
                    rawDisplayText,
                    displayText
            ));
        }

        resolvedRows.sort(Comparator.comparing(ResolvedActivityRow::studentUserId).thenComparing(ResolvedActivityRow::rowNo));
        List<ActivityImportedComponent> components = resolvedRows.stream()
                .map(row -> new ActivityImportedComponent(
                        row.rowNo(),
                        row.studentUserId(),
                        row.studentNo(),
                        item.itemCode(),
                        item.categoryCode(),
                        request.scoreValue(),
                        row.rawDisplayText(),
                        row.displayText(),
                        activityBatchId
                ))
                .toList();
        if (!components.isEmpty()) {
            failedRows.addAll(repository.insertActivityComponents(request.academicYear(), components));
        }

        failedRows.sort(Comparator.comparing(ActivityImportFailedRow::rowNo));
        long totalCount = preparedRows.totalCount();
        long failedCount = failedRows.size();
        return new ActivityImportResult(
                activityBatchId,
                request.title(),
                item.itemCode(),
                request.scoreValue(),
                totalCount,
                totalCount - failedCount,
                failedCount,
                List.copyOf(failedRows)
        );
    }

    private PreparedActivityRows prepareRows(List<ActivityImportRow> rows) {
        List<ActivityImportFailedRow> failedRows = new ArrayList<>();
        List<FieldValidActivityRow> fieldValidRows = new ArrayList<>();
        Set<String> seenStudentNos = new LinkedHashSet<>();

        for (ActivityImportRow row : rows) {
            Optional<ActivityImportFailedRow> fieldFailure = validateFields(row);
            if (fieldFailure.isPresent()) {
                failedRows.add(fieldFailure.get());
                continue;
            }

            String studentNo = row.studentNo().trim();
            if (!seenStudentNos.add(studentNo)) {
                failedRows.add(failed(row, "DUPLICATE_STUDENT", "同一活动批次中学生重复"));
                continue;
            }
            fieldValidRows.add(new FieldValidActivityRow(row, studentNo));
        }

        return new PreparedActivityRows(rows.size(), failedRows, fieldValidRows);
    }

    private NormalizedRequest normalize(ImportActivitiesCommand command) {
        if (command.fileContent() == null || command.fileContent().length == 0) {
            throw new ValidationException("上传文件不能为空");
        }
        String title = normalizeTitle(command.title());
        String itemCode = normalizeItemCode(command.itemCode());
        BigDecimal scoreValue = normalizeScoreValue(command.scoreValue());
        LocalDateTime heldAt = normalizeHeldAt(command.heldAt());
        String academicYear = normalizeAcademicYear(command.academicYear());
        return new NormalizedRequest(title, itemCode, scoreValue, heldAt, academicYear);
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

    private String normalizeItemCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new ValidationException("itemCode 不能为空");
        }
        String value = itemCode.trim();
        if (value.codePointCount(0, value.length()) > 64) {
            throw new ValidationException("itemCode 长度不能超过 64");
        }
        return value;
    }

    private BigDecimal normalizeScoreValue(String scoreValue) {
        if (scoreValue == null || scoreValue.trim().isEmpty()) {
            throw new ValidationException("scoreValue 必须是数字");
        }
        String value = scoreValue.trim();
        if (!STRICT_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new ValidationException("scoreValue 必须是数字");
        }
        BigDecimal score = new BigDecimal(value);
        if (score.compareTo(MAX_SCORE) > 0) {
            throw new ValidationException("scoreValue 必须在 0 到 99999999.99 之间");
        }
        if (score.scale() > 2) {
            throw new ValidationException("scoreValue 最多保留 2 位小数");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
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

    private String activityBatchId(NormalizedRequest request) {
        String year = request.academicYear().replace("-", "");
        String held = request.heldAt().format(BATCH_TIME_FORMATTER);
        return "ACTIVITY-" + year + "-" + held + "-" + sha256Prefix(hashInput(request, held));
    }

    private String hashInput(NormalizedRequest request, String held) {
        return String.join("|",
                encodeHashPart(request.academicYear()),
                encodeHashPart(held),
                encodeHashPart(request.title()),
                encodeHashPart(request.itemCode()),
                encodeHashPart(request.scoreValue().toPlainString())
        );
    }

    private String encodeHashPart(String value) {
        return value.codePointCount(0, value.length()) + ":" + value;
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

    private Optional<ActivityImportFailedRow> validateFields(ActivityImportRow row) {
        if (isBlank(row.studentNo())) {
            return Optional.of(failed(row, "STUDENT_NO_REQUIRED", "studentNo 不能为空"));
        }
        String studentNo = row.studentNo().trim();
        if (studentNo.codePointCount(0, studentNo.length()) > 64) {
            return Optional.of(failed(row, "STUDENT_NOT_FOUND", "studentNo 对应学生不存在或未启用"));
        }
        if (row.displayText() != null) {
            String displayText = row.displayText().trim();
            if (displayText.codePointCount(0, displayText.length()) > 1000) {
                return Optional.of(failed(row, "DISPLAY_TEXT_TOO_LONG", "displayText 长度不能超过 1000"));
            }
        }
        return Optional.empty();
    }

    private ActivityImportFailedRow failed(ActivityImportRow row, String code, String message) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("studentNo", blankToNull(row.studentNo()));
        raw.put("displayText", blankToNull(row.displayText()));
        return new ActivityImportFailedRow(row.rowNo(), code, message, raw);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean canAccess(UserAuthorizationContext context,
                              ActivityImportStudentTarget target,
                              Map<Long, Optional<String>> orgPathCache) {
        for (IamScopeRule rule : context.findScopeRulesByPermissionCode(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            if (!"ACTIVE".equals(rule.status())) {
                continue;
            }
            if ("GLOBAL".equals(rule.scopeType())) {
                return true;
            }
            if ("ORG_UNIT".equals(rule.scopeType()) && rule.orgUnitId() != null && rule.orgUnitId().equals(target.orgUnitId())) {
                return true;
            }
            if ("ORG_SUBTREE".equals(rule.scopeType()) && matchesOrgSubtree(rule.orgUnitId(), target.orgUnitId(), orgPathCache)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOrgSubtree(Long rootOrgUnitId,
                                      Long targetOrgUnitId,
                                      Map<Long, Optional<String>> orgPathCache) {
        if (rootOrgUnitId == null || targetOrgUnitId == null) {
            return false;
        }
        Optional<String> rootPath = orgPathCache.computeIfAbsent(rootOrgUnitId, repository::findActiveOrgPath);
        Optional<String> targetPath = orgPathCache.computeIfAbsent(targetOrgUnitId, repository::findActiveOrgPath);
        if (rootPath.isEmpty() || targetPath.isEmpty() || isBlank(rootPath.get()) || isBlank(targetPath.get())) {
            return false;
        }
        String normalizedRootPath = trimTrailingSlash(rootPath.get().trim());
        String normalizedTargetPath = trimTrailingSlash(targetPath.get().trim());
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

    private record NormalizedRequest(
            String title,
            String itemCode,
            BigDecimal scoreValue,
            LocalDateTime heldAt,
            String academicYear
    ) {
    }

    private record PreparedActivityRows(
            long totalCount,
            List<ActivityImportFailedRow> failedRows,
            List<FieldValidActivityRow> fieldValidRows
    ) {
    }

    private record FieldValidActivityRow(
            ActivityImportRow row,
            String studentNo
    ) {
    }

    private record ResolvedActivityRow(
            Long rowNo,
            Long studentUserId,
            String studentNo,
            String rawDisplayText,
            String displayText
    ) {
    }
}
