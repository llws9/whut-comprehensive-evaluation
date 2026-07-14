package edu.whut.eval.application.finalrecord.repository;

import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportRow;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.shared.PageResult;

import java.util.List;
import java.util.Optional;

public interface FinalRecordQueryRepository {

    Optional<FinalRecordQueryRow> findStudentFinalRecord(long studentUserId, String academicYear);

    List<FinalComponentScoreRow> listStudentFinalRecordComponents(long finalRecordId);

    PageResult<FinalRecordQueryRow> pageAdminFinalRecords(FinalRecordAccessContext accessContext,
                                                          FinalRecordPageQuery query);

    List<FinalScoreExportRow> listAdminFinalScoreExportRows(FinalRecordAccessContext accessContext,
                                                            FinalScoreExportQuery query,
                                                            int limit);

    Optional<FinalRecordQueryRow> findAdminFinalRecordDetail(long finalRecordId);

    List<FinalComponentScoreRow> listAdminFinalRecordComponents(long finalRecordId);
}
