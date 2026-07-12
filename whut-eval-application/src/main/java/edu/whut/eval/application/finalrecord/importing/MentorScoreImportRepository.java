package edu.whut.eval.application.finalrecord.importing;

import java.util.Optional;

public interface MentorScoreImportRepository {

    Optional<MentorScoreImportStudentTarget> findTarget(String studentNo, String academicYear);

    Optional<String> findActiveOrgPath(Long orgUnitId);

    boolean importedComponentExists(Long studentUserId, String academicYear, String categoryCode, String itemCode);

    void upsertDraftComponent(MentorScoreImportedComponent component, String importBatchId);
}
