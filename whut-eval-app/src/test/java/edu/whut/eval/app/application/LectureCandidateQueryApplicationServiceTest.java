package edu.whut.eval.app.application;

import edu.whut.eval.application.application.query.LectureCandidateView;
import edu.whut.eval.application.application.service.LectureCandidateQueryApplicationService;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import edu.whut.eval.domain.application.repository.LectureCandidateQueryRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LectureCandidateQueryApplicationServiceTest {

    private UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private LectureCandidateQueryRepository lectureCandidateQueryRepository;
    private LectureCandidateQueryApplicationService service;

    @BeforeEach
    void setUp() {
        userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
        lectureCandidateQueryRepository = mock(LectureCandidateQueryRepository.class);
        service = new LectureCandidateQueryApplicationService(userAuthorizationContextAssembler, lectureCandidateQueryRepository);
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext());
    }

    @Test
    void shouldQueryCurrentStudentsImportedLectureCandidatesAndMapHeldAtFromBatchId() {
        given(lectureCandidateQueryRepository.pageStudentLectureCandidates(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any(LectureCandidatePageQuery.class)
        )).willReturn(new PageResult<>(1, List.of(new LectureCandidateRecord(
                7001L,
                "学院学术讲座 讲座签到",
                "2025-2026",
                new BigDecimal("1.25"),
                "LECTURE-20252026-20260518143000-ABCDEF123456",
                LocalDateTime.of(2026, 5, 18, 14, 31)
        ))));

        PageResult<LectureCandidateView> result = service.pageCurrentStudentLectures(
                new LectureCandidatePageQuery("2025-2026", "学术", 2, 5)
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        LectureCandidateView lecture = result.records().getFirst();
        assertThat(lecture.lectureId()).isEqualTo(7001L);
        assertThat(lecture.title()).isEqualTo("学院学术讲座 讲座签到");
        assertThat(lecture.heldAt()).isEqualTo("2026-05-18T14:30:00");
        assertThat(lecture.academicYear()).isEqualTo("2025-2026");
        assertThat(lecture.maxScore()).isEqualByComparingTo("1.25");
        assertThat(lecture.attendanceStatus()).isEqualTo("CLAIMED");

        ArgumentCaptor<LectureCandidatePageQuery> queryCaptor = ArgumentCaptor.forClass(LectureCandidatePageQuery.class);
        verify(lectureCandidateQueryRepository).pageStudentLectureCandidates(
                org.mockito.ArgumentMatchers.eq(1001L),
                queryCaptor.capture()
        );
        assertThat(queryCaptor.getValue().keyword()).isEqualTo("学术");
        assertThat(queryCaptor.getValue().offset()).isEqualTo(5);
    }

    @Test
    void shouldFallbackToComponentCreatedAtWhenBatchIdDoesNotContainHeldAt() {
        given(lectureCandidateQueryRepository.pageStudentLectureCandidates(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any(LectureCandidatePageQuery.class)
        )).willReturn(new PageResult<>(1, List.of(new LectureCandidateRecord(
                7002L,
                "历史导入讲座",
                "2025-2026",
                new BigDecimal("1.00"),
                "legacy-batch",
                LocalDateTime.of(2026, 6, 1, 9, 0)
        ))));

        PageResult<LectureCandidateView> result = service.pageCurrentStudentLectures(
                new LectureCandidatePageQuery("2025-2026", null, 1, 20)
        );

        assertThat(result.records().getFirst().heldAt()).isEqualTo("2026-06-01T09:00:00");
    }

    private static UserAuthorizationContext studentContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "张三",
                "student",
                Set.of(),
                Set.of(AuthorizationPermissionCodes.APPLICATION_VIEW_SELF),
                List.of()
        );
    }
}
