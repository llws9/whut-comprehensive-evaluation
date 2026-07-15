package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import edu.whut.eval.domain.application.repository.LectureCandidateQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.persistence.mapper.LectureCandidateQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisLectureCandidateQueryRepository implements LectureCandidateQueryRepository {

    private final LectureCandidateQueryMapper lectureCandidateQueryMapper;

    public MybatisLectureCandidateQueryRepository(LectureCandidateQueryMapper lectureCandidateQueryMapper) {
        this.lectureCandidateQueryMapper = lectureCandidateQueryMapper;
    }

    @Override
    public PageResult<LectureCandidateRecord> pageStudentLectureCandidates(Long studentUserId, LectureCandidatePageQuery query) {
        long total = lectureCandidateQueryMapper.countStudentLectureCandidates(studentUserId, query);
        if (total == 0) {
            return new PageResult<>(0, List.of());
        }
        return new PageResult<>(
                total,
                lectureCandidateQueryMapper.selectStudentLectureCandidates(studentUserId, query)
        );
    }
}
