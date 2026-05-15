package edu.whut.eval.app.query;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.score.query.ScoreRecordView;
import edu.whut.eval.application.score.service.ScoreQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.score.model.ScoreRecord;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.score.repository.ScoreQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScoreQueryApplicationServiceTest {

    @Mock
    private UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    @Mock
    private ScoreQueryRepository scoreQueryRepository;

    @InjectMocks
    private ScoreQueryApplicationService scoreQueryApplicationService;

    @Test
    void shouldPageAccessibleScoresWithDefaultPermission() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
                List.of()
        );
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(authorizationContext);
        given(scoreQueryRepository.pageAccessibleScores(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageResult<>(1, List.of(
                        new ScoreRecord(8001L, 1001L, 3001L, "/1/3001/", "ACADEMIC", "LECTURE", "2025-2026")
                )));

        PageResult<ScoreRecordView> result = scoreQueryApplicationService.pageAccessibleScores(
                new ScorePageQuery(1, 10, null, null, null, null, null, null)
        );

        ArgumentCaptor<edu.whut.eval.domain.score.query.ScoreAccessContext> captor =
                ArgumentCaptor.forClass(edu.whut.eval.domain.score.query.ScoreAccessContext.class);
        org.mockito.Mockito.verify(scoreQueryRepository).pageAccessibleScores(
                captor.capture(),
                org.mockito.ArgumentMatchers.any(ScorePageQuery.class)
        );

        assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ScoreRecordView::getScoreId).containsExactly(8001L);
    }

    @Test
    void shouldPageAccessibleScoresWithSpecifiedStudentPermission() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.SCORE_VIEW_SELF),
                List.of()
        );
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(authorizationContext);
        given(scoreQueryRepository.pageAccessibleScores(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageResult<>(1, List.of(
                        new ScoreRecord(8001L, 1001L, 3001L, "/1/3001/", "ACADEMIC", "LECTURE", "2025-2026")
                )));

        PageResult<ScoreRecordView> result = scoreQueryApplicationService.pageAccessibleScores(
                new ScorePageQuery(1, 10, null, null, null, null, null, null),
                AuthorizationPermissionCodes.SCORE_VIEW_SELF
        );

        ArgumentCaptor<edu.whut.eval.domain.score.query.ScoreAccessContext> captor =
                ArgumentCaptor.forClass(edu.whut.eval.domain.score.query.ScoreAccessContext.class);
        org.mockito.Mockito.verify(scoreQueryRepository).pageAccessibleScores(
                captor.capture(),
                org.mockito.ArgumentMatchers.any(ScorePageQuery.class)
        );

        assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.SCORE_VIEW_SELF);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ScoreRecordView::getScoreId).containsExactly(8001L);
    }

    @Test
    void shouldRejectWhenCurrentUserDoesNotHaveScorePermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                List.of()
        ));

        assertThatThrownBy(() -> scoreQueryApplicationService.pageAccessibleScores(
                new ScorePageQuery(1, 10, null, null, null, null, null, null)
        ))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权限访问成绩列表");
    }

    @Test
    void shouldRejectWhenCurrentUserDoesNotHaveSpecifiedStudentPermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
                List.of()
        ));

        assertThatThrownBy(() -> scoreQueryApplicationService.pageAccessibleScores(
                new ScorePageQuery(1, 10, null, null, null, null, null, null),
                AuthorizationPermissionCodes.SCORE_VIEW_SELF
        ))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权限访问成绩列表");
    }
}
