package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportResult;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ActivityImportContractsCompileTest {
    private ActivityImportContractsCompileTest() {
    }

    static ActivityImportResult constructContracts(ActivityImportParser parser,
                                                   ActivityImportBatchLock lock,
                                                   ActivityImportRepository repository) {
        ActivityImportRow row = new ActivityImportRow(2L, "2022305001", "签到");
        ActivityImportFailedRow failure = new ActivityImportFailedRow(
                2L,
                "STUDENT_NOT_FOUND",
                "studentNo 对应学生不存在或未启用",
                Map.of("studentNo", "2022305001", "displayText", "签到")
        );
        ImportActivitiesCommand command = new ImportActivitiesCommand(
                new byte[]{1},
                "校运会志愿服务",
                "SPORTS_COMPETITION",
                "0.50",
                "2026-05-18T14:30",
                "2025-2026"
        );
        ActivityImportResult result = new ActivityImportResult(
                "ACTIVITY-20252026-20260518143000-ABCDEF123456",
                command.title(),
                command.itemCode(),
                new BigDecimal(command.scoreValue()),
                1,
                0,
                1,
                List.of(failure)
        );
        ActivityImportedComponent component = new ActivityImportedComponent(
                row.rowNo(),
                1001L,
                row.studentNo(),
                result.itemCode(),
                "SPORTS",
                new BigDecimal("0.50"),
                row.displayText(),
                row.displayText(),
                result.activityBatchId()
        );

        List<ActivityImportRow> parsedRows = parser.parse(command.fileContent());
        boolean locked = lock.tryAcquire(result.activityBatchId(), Duration.ofSeconds(1));
        Optional<ActivityImportItemDefinition> item = repository.findActiveSportsItem(result.itemCode());
        Optional<ActivityImportStudentTarget> target = repository.findTarget(row.studentNo(), "2025-2026");
        Optional<String> orgPath = repository.findActiveOrgPath(target.map(ActivityImportStudentTarget::orgUnitId).orElse(0L));
        boolean exists = repository.activityBatchExists("2025-2026", "SPORTS", result.itemCode(), result.activityBatchId());
        List<ActivityImportFailedRow> repositoryFailures = repository.insertActivityComponents(
                "2025-2026",
                List.of(component)
        );
        lock.release(result.activityBatchId());

        long totalCount = parsedRows.size()
                + (locked ? 0 : 1)
                + item.stream().count()
                + target.stream().count()
                + orgPath.stream().count()
                + (exists ? 1 : 0);
        return new ActivityImportResult(
                result.activityBatchId(),
                result.title(),
                result.itemCode(),
                result.scoreValue(),
                totalCount,
                result.successCount(),
                repositoryFailures.size() + failure.rowNo(),
                repositoryFailures
        );
    }
}
