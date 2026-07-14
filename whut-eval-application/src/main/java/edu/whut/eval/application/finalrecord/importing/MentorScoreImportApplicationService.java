package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportMode;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportResult;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MentorScoreImportApplicationService {

    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Set<String> CATEGORY_CODES = Set.of("MORAL", "INTELLECTUAL", "SPORTS", "LABOR");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("99999999.99");
    private static final String DEFAULT_DISPLAY_TEXT = "导师/固定成绩导入";

    private final UserAuthorizationContextAssembler authorizationContextAssembler;
    private final MentorScoreImportParser parser;
    private final MentorScoreImportRepository repository;

    public MentorScoreImportApplicationService(UserAuthorizationContextAssembler authorizationContextAssembler,
                                               MentorScoreImportParser parser,
                                               MentorScoreImportRepository repository) {
        this.authorizationContextAssembler = authorizationContextAssembler;
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public MentorScoreImportResult importMentorScores(ImportMentorScoresCommand command) {
        String academicYear = normalizeAcademicYear(command.academicYear());
        MentorScoreImportMode mode = parseMode(command.importMode());
        UserAuthorizationContext context = authorizationContextAssembler.requiredAuthorizationContext();
        if (!context.hasAuthority(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            throw new AccessDeniedAppException("当前用户无导入权限");
        }

        String importBatchId = "D7-" + UUID.randomUUID();
        List<MentorScoreImportRow> rows = parser.parse(command.fileContent());
        List<MentorScoreImportFailedRow> failedRows = new ArrayList<>();
        List<ResolvedImportRow> candidateRows = new ArrayList<>();

        for (MentorScoreImportRow row : rows) {
            Optional<MentorScoreImportFailedRow> fieldFailure = validateFields(row);
            if (fieldFailure.isPresent()) {
                failedRows.add(fieldFailure.get());
                continue;
            }

            Optional<MentorScoreImportStudentTarget> target = repository.findTarget(row.studentNo().trim(), academicYear);
            if (target.isEmpty()) {
                failedRows.add(failed(row, "STUDENT_NOT_FOUND", "studentNo 对应学生不存在或未启用"));
                continue;
            }

            candidateRows.add(new ResolvedImportRow(row, target.get(), new BigDecimal(row.scoreValue().trim())));
        }

        if (mode == MentorScoreImportMode.STRICT_INSERT) {
            assertStrictInsertHasNoDuplicates(academicYear, candidateRows);
        }

        List<ResolvedImportRow> resolvedRows = new ArrayList<>();
        for (ResolvedImportRow candidateRow : candidateRows) {
            if (!canAccess(context, candidateRow.target())) {
                failedRows.add(failed(candidateRow.row(), "OUT_OF_SCOPE", "当前用户无权导入该学生成绩"));
                continue;
            }
            if (isLocked(candidateRow.target().finalRecordStatus())) {
                failedRows.add(failed(candidateRow.row(), "FINAL_RECORD_LOCKED", "已提交或已确认的最终成绩不允许导入覆盖"));
                continue;
            }
            resolvedRows.add(candidateRow);
        }

        for (ResolvedImportRow resolvedRow : resolvedRows) {
            repository.upsertDraftComponent(toComponent(resolvedRow, academicYear, importBatchId), importBatchId);
        }

        long totalCount = rows.size();
        long successCount = resolvedRows.size();
        return new MentorScoreImportResult(
                importBatchId,
                totalCount,
                successCount,
                failedRows.size(),
                List.copyOf(failedRows),
                Instant.now()
        );
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

    private MentorScoreImportMode parseMode(String importMode) {
        if (importMode == null || importMode.isBlank()) {
            return MentorScoreImportMode.UPSERT;
        }
        try {
            return MentorScoreImportMode.valueOf(importMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("importMode 仅允许 UPSERT 或 STRICT_INSERT");
        }
    }

    private Optional<MentorScoreImportFailedRow> validateFields(MentorScoreImportRow row) {
        if (isBlank(row.studentNo())) {
            return Optional.of(failed(row, "STUDENT_NO_REQUIRED", "studentNo 不能为空"));
        }
        if (isBlank(row.categoryCode())) {
            return Optional.of(failed(row, "CATEGORY_CODE_REQUIRED", "categoryCode 不能为空"));
        }
        if (!CATEGORY_CODES.contains(row.categoryCode().trim())) {
            return Optional.of(failed(row, "CATEGORY_CODE_INVALID", "categoryCode 仅允许 MORAL、INTELLECTUAL、SPORTS、LABOR"));
        }
        if (isBlank(row.itemCode())) {
            return Optional.of(failed(row, "ITEM_CODE_REQUIRED", "itemCode 不能为空"));
        }
        if (row.itemCode().trim().length() > 64) {
            return Optional.of(failed(row, "ITEM_CODE_TOO_LONG", "itemCode 长度不能超过 64"));
        }
        if (isBlank(row.scoreValue())) {
            return Optional.of(failed(row, "SCORE_VALUE_REQUIRED", "scoreValue 不能为空"));
        }
        BigDecimal scoreValue;
        try {
            scoreValue = new BigDecimal(row.scoreValue().trim());
        } catch (NumberFormatException ex) {
            return Optional.of(failed(row, "SCORE_VALUE_INVALID", "scoreValue 必须是数字"));
        }
        if (scoreValue.compareTo(MIN_SCORE) < 0 || scoreValue.compareTo(MAX_SCORE) > 0) {
            return Optional.of(failed(row, "SCORE_VALUE_OUT_OF_RANGE", "scoreValue 必须在 0 到 99999999.99 之间"));
        }
        if (scoreValue.stripTrailingZeros().scale() > 2) {
            return Optional.of(failed(row, "SCORE_VALUE_SCALE_INVALID", "scoreValue 最多保留 2 位小数"));
        }
        if (row.displayText() != null && row.displayText().length() > 1000) {
            return Optional.of(failed(row, "DISPLAY_TEXT_TOO_LONG", "displayText 长度不能超过 1000"));
        }
        if (row.sourceRefId() != null && row.sourceRefId().length() > 64) {
            return Optional.of(failed(row, "SOURCE_REF_ID_TOO_LONG", "sourceRefId 长度不能超过 64"));
        }
        return Optional.empty();
    }

    private boolean canAccess(UserAuthorizationContext context, MentorScoreImportStudentTarget target) {
        List<IamScopeRule> rules = context.findScopeRulesByPermissionCode(AuthorizationPermissionCodes.SCORE_IMPORT);
        for (IamScopeRule rule : rules) {
            if (!"ACTIVE".equals(rule.status())) {
                continue;
            }
            if ("ALL".equals(rule.scopeType())) {
                return true;
            }
            if ("ORG_UNIT".equals(rule.scopeType()) && rule.orgUnitId() != null && rule.orgUnitId().equals(target.orgUnitId())) {
                return true;
            }
            if ("ORG_SUBTREE".equals(rule.scopeType()) && matchesOrgSubtree(rule.orgUnitId(), target.orgPath())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOrgSubtree(Long rootOrgUnitId, String targetPath) {
        if (rootOrgUnitId == null || isBlank(targetPath)) {
            return false;
        }
        Optional<String> rootPath = repository.findActiveOrgPath(rootOrgUnitId);
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

    private boolean isLocked(String finalRecordStatus) {
        return "SUBMITTED".equals(finalRecordStatus) || "CONFIRMED".equals(finalRecordStatus);
    }

    private void assertStrictInsertHasNoDuplicates(String academicYear, List<ResolvedImportRow> resolvedRows) {
        Set<ImportTargetKey> workbookKeys = new LinkedHashSet<>();
        for (ResolvedImportRow resolvedRow : resolvedRows) {
            ImportTargetKey key = key(resolvedRow);
            if (!workbookKeys.add(key)) {
                throw new ConflictException("STRICT_INSERT 模式不允许覆盖");
            }
            if (repository.importedComponentExists(
                    key.studentUserId(),
                    academicYear,
                    key.categoryCode(),
                    key.itemCode())) {
                throw new ConflictException("STRICT_INSERT 模式不允许覆盖");
            }
        }
    }

    private ImportTargetKey key(ResolvedImportRow resolvedRow) {
        return new ImportTargetKey(
                resolvedRow.target().studentUserId(),
                resolvedRow.row().categoryCode().trim(),
                resolvedRow.row().itemCode().trim()
        );
    }

    private MentorScoreImportedComponent toComponent(ResolvedImportRow resolvedRow, String academicYear, String importBatchId) {
        MentorScoreImportRow row = resolvedRow.row();
        return new MentorScoreImportedComponent(
                row.rowNo(),
                resolvedRow.target().studentUserId(),
                academicYear,
                row.categoryCode().trim(),
                row.itemCode().trim(),
                resolvedRow.scoreValue(),
                isBlank(row.displayText()) ? DEFAULT_DISPLAY_TEXT : row.displayText().trim(),
                isBlank(row.sourceRefId()) ? importBatchId + ":" + row.rowNo() : row.sourceRefId().trim()
        );
    }

    private MentorScoreImportFailedRow failed(MentorScoreImportRow row, String code, String message) {
        return new MentorScoreImportFailedRow(row.rowNo(), code, message, rawValue(row));
    }

    private Map<String, String> rawValue(MentorScoreImportRow row) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("studentNo", row.studentNo());
        values.put("categoryCode", row.categoryCode());
        values.put("itemCode", row.itemCode());
        values.put("scoreValue", row.scoreValue());
        values.put("displayText", row.displayText());
        values.put("sourceRefId", row.sourceRefId());
        return values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedImportRow(
            MentorScoreImportRow row,
            MentorScoreImportStudentTarget target,
            BigDecimal scoreValue
    ) {
    }

    private record ImportTargetKey(
            Long studentUserId,
            String categoryCode,
            String itemCode
    ) {
    }
}
