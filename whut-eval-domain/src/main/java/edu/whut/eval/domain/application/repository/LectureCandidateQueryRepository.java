package edu.whut.eval.domain.application.repository;

import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import edu.whut.eval.domain.shared.PageResult;

public interface LectureCandidateQueryRepository {

    PageResult<LectureCandidateRecord> pageStudentLectureCandidates(Long studentUserId, LectureCandidatePageQuery query);
}
