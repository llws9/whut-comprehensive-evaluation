package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class LectureImportContractsCompileTest {
    private LectureImportContractsCompileTest() {
    }

    static LectureImportResult constructContracts(LectureImportParser parser,
                                                  LectureImportBatchLock lock,
                                                  LectureImportRepository repository) {
        LectureImportRow row = new LectureImportRow(2L, "S1", "1.00", "display");
        LectureImportFailedRow failedRow = new LectureImportFailedRow(
                row.rowNo(),
                "CODE",
                "message",
                Map.of("studentNo", row.studentNo(), "scoreValue", row.scoreValue())
        );
        ImportLecturesCommand command = new ImportLecturesCommand(
                new byte[]{1},
                "title",
                "2026-05-18T14:30",
                "2025-2026"
        );
        LectureImportStudentTarget target = new LectureImportStudentTarget(1L, row.studentNo(), 2002L, "/WHUT/CS");
        LectureImportedComponent component = new LectureImportedComponent(
                row.rowNo(),
                target.studentUserId(),
                target.studentNo(),
                row.scoreValue(),
                new BigDecimal(row.scoreValue()),
                row.displayText(),
                row.displayText()
        );

        List<LectureImportRow> parsedRows = parser.parse(command.fileContent());
        boolean locked = lock.tryAcquire("batch", Duration.ofSeconds(5));
        Optional<LectureImportStudentTarget> found = repository.findTarget(target.studentNo(), command.academicYear());
        Optional<String> orgPath = repository.findActiveOrgPath(target.orgUnitId());
        boolean exists = repository.lectureBatchExists(command.academicYear(), "batch");
        List<LectureImportFailedRow> failedRows = repository.insertLectureComponents(
                command.academicYear(),
                "batch",
                List.of(component)
        );
        lock.release("batch");

        long totalCount = parsedRows.size() + (locked ? 0 : 1) + found.stream().count() + orgPath.stream().count();
        return new LectureImportResult(
                "batch",
                command.title(),
                LocalDateTime.parse("2026-05-18T14:30:00"),
                command.academicYear(),
                totalCount,
                exists ? 0 : 1,
                failedRows.size() + failedRow.rowNo(),
                failedRows
        );
    }
}
