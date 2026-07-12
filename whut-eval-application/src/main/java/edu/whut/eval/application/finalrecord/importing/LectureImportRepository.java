package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;

import java.util.List;
import java.util.Optional;

public interface LectureImportRepository {
    boolean lectureBatchExists(String academicYear, String lectureBatchId);

    /**
     * The academicYear argument is reserved for historical-organization lookup; Minimal D-8 reads current membership only.
     */
    Optional<LectureImportStudentTarget> findTarget(String studentNo, String academicYear);

    Optional<String> findActiveOrgPath(Long orgUnitId);

    List<LectureImportFailedRow> insertLectureComponents(String academicYear,
                                                         String lectureBatchId,
                                                         List<LectureImportedComponent> components);
}
