package edu.whut.eval.domain.finalrecord.repository;

import edu.whut.eval.domain.finalrecord.model.FinalComponentScore;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;

import java.util.List;
import java.util.Optional;

public interface FinalRecordRepository {

    Optional<FinalRecord> findByStudentAndAcademicYear(long studentUserId, String academicYear);

    Optional<FinalRecord> findById(long finalRecordId);

    AggregatedFinalRecordSnapshot aggregateApprovedFacts(long studentUserId, String academicYear);

    FinalRecord insertDraft(FinalRecord record);

    void deleteDraft(long finalRecordId);

    void deleteComponents(long finalRecordId);

    void batchInsertComponents(long finalRecordId, List<FinalComponentScore> components);

    FinalRecord updateTransition(FinalRecord record);

    List<FinalComponentScore> listComponents(long finalRecordId);
}
