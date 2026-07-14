package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;

import java.util.List;
import java.util.Optional;

public interface ActivityImportRepository {
    Optional<ActivityImportItemDefinition> findActiveSportsItem(String itemCode);

    boolean activityBatchExists(String academicYear, String categoryCode, String itemCode, String activityBatchId);

    /**
     * Minimal D-9 intentionally reads current primary active membership only.
     * The academicYear argument is retained for D-7/D-8 parity and a future historical-membership lookup.
     */
    Optional<ActivityImportStudentTarget> findTarget(String studentNo, String academicYear);

    Optional<String> findActiveOrgPath(Long orgUnitId);

    List<ActivityImportFailedRow> insertActivityComponents(String academicYear, List<ActivityImportedComponent> components);
}
