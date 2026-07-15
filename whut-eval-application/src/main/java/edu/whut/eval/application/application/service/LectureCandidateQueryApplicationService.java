package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.LectureCandidateView;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import edu.whut.eval.domain.application.repository.LectureCandidateQueryRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class LectureCandidateQueryApplicationService {

    private static final DateTimeFormatter BATCH_HELD_AT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter RESPONSE_HELD_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CLAIMED = "CLAIMED";

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final LectureCandidateQueryRepository lectureCandidateQueryRepository;

    public LectureCandidateQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                   LectureCandidateQueryRepository lectureCandidateQueryRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.lectureCandidateQueryRepository = lectureCandidateQueryRepository;
    }

    public PageResult<LectureCandidateView> pageCurrentStudentLectures(LectureCandidatePageQuery query) {
        UserAuthorizationContext context = userAuthorizationContextAssembler.requiredAuthorizationContext();
        PageResult<LectureCandidateRecord> page = lectureCandidateQueryRepository.pageStudentLectureCandidates(
                context.getUserId(),
                query
        );
        return new PageResult<>(page.total(), page.records().stream().map(this::toView).toList());
    }

    private LectureCandidateView toView(LectureCandidateRecord record) {
        return new LectureCandidateView(
                record.lectureId(),
                record.title(),
                resolveHeldAt(record),
                record.academicYear(),
                record.maxScore(),
                CLAIMED
        );
    }

    private String resolveHeldAt(LectureCandidateRecord record) {
        LocalDateTime parsed = parseHeldAtFromBatchId(record.sourceRefId());
        return (parsed == null ? record.createdAt() : parsed).format(RESPONSE_HELD_AT_FORMAT);
    }

    private LocalDateTime parseHeldAtFromBatchId(String sourceRefId) {
        if (sourceRefId == null) {
            return null;
        }
        String[] parts = sourceRefId.split("-");
        if (parts.length < 3 || !"LECTURE".equals(parts[0])) {
            return null;
        }
        try {
            return LocalDateTime.parse(parts[2], BATCH_HELD_AT_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
