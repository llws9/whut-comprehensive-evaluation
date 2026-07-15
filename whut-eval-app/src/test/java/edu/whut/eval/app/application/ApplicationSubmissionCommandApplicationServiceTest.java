package edu.whut.eval.app.application;

import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.DeleteApplicationCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.command.UpdateApplicationDraftCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.application.application.service.ApplicationOrgMembershipValidator;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import edu.whut.eval.domain.config.RuleEngineService;
import edu.whut.eval.domain.config.StudentContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationSubmissionCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
    private final ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy = mock(ApplicationSubmissionWindowPolicy.class);
    private final ActiveSubmissionPolicy activeSubmissionPolicy = mock(ActiveSubmissionPolicy.class);
    private final ApplicationAttachmentResolver applicationAttachmentResolver = mock(ApplicationAttachmentResolver.class);
    private final RuleEngineService ruleEngineService = mock(RuleEngineService.class);
    private final ApplicationOrgMembershipValidator applicationOrgMembershipValidator = mock(ApplicationOrgMembershipValidator.class);

    private final ApplicationSubmissionCommandApplicationService applicationService =
            new ApplicationSubmissionCommandApplicationService(
                    userAuthorizationContextAssembler,
                    applicationSubmissionRepository,
                    applicationSubmissionWindowPolicy,
                    activeSubmissionPolicy,
                    applicationAttachmentResolver,
                    ruleEngineService,
                    applicationOrgMembershipValidator
            );

    @Test
    void shouldCreateDraftForCurrentUser() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(activeSubmissionPolicy.hasActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).willReturn(false);
        given(applicationAttachmentResolver.resolveForBinding(List.of("file-1"), 1001L))
                .willReturn(List.of(sampleAttachment()));
        given(applicationSubmissionRepository.save(any(ApplicationSubmission.class)))
                .willReturn(savedDraft());

        ApplicationSubmissionView result = applicationService.createDraft(new CreateApplicationDraftCommand(
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of("file-1")
        ));

        assertThat(result.getApplicationId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(ApplicationSubmissionStatus.DRAFT);
        assertThat(result.getAttachmentCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectWhenActiveSubmissionExists() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(activeSubmissionPolicy.hasActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).willReturn(true);

        assertThatThrownBy(() -> applicationService.createDraft(new CreateApplicationDraftCommand(
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of("file-1")
        ))).isInstanceOf(ConflictException.class)
                .hasMessage("当前项目在该学年学期下已存在活跃申请");
    }

    @Test
    void shouldSubmitOwnedSubmissionWhenWindowOpen() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1"))
                .willReturn(true);
        given(applicationSubmissionRepository.save(any(ApplicationSubmission.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ApplicationSubmissionView result = applicationService.submit(new SubmitApplicationCommand(1L, 0L));

        assertThat(result.getStatus()).isEqualTo(ApplicationSubmissionStatus.SUBMITTED);
        verify(applicationSubmissionRepository).save(any(ApplicationSubmission.class));
    }

    @Test
    void shouldRejectWhenAttachmentFileIsInaccessible() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(activeSubmissionPolicy.hasActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).willReturn(false);
        given(applicationAttachmentResolver.resolveForBinding(List.of("file-1"), 1001L))
                .willThrow(new ValidationException("当前用户无权使用指定附件"));

        assertThatThrownBy(() -> applicationService.createDraft(new CreateApplicationDraftCommand(
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of("file-1")
        ))).isInstanceOf(ValidationException.class)
                .hasMessage("当前用户无权使用指定附件");
    }

    @Test
    void shouldReturnAccessDeniedWhenUpdatingAnotherUsersApplication() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(new ApplicationSubmission(
                1L, 2002L, 10L, "competition", "item-1", "2025-2026", "1",
                "申请标题", "申请说明", List.of(sampleAttachment()), ApplicationSubmissionStatus.DRAFT,
                null, Instant.now(), Instant.now(), 0L
        )));

        assertThatThrownBy(() -> applicationService.updateDraft(new UpdateApplicationDraftCommand(
                1L, "新标题", "新说明", List.of("file-1"), 0L
        ))).isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权操作该申请");
    }

    @Test
    void shouldRejectCreateDraftForOrgUnitOutsideCurrentUserMemberships() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 9999L)).willReturn(false);

        assertThatThrownBy(() -> applicationService.createDraft(new CreateApplicationDraftCommand(
                9999L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of("file-1")
        ))).isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户不属于该组织");
    }

    @Test
    void shouldRejectSubmitWithoutOptionCodeWhenItemHasOptions() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1"))
                .willReturn(true);
        given(ruleEngineService.requiresOption("item-1")).willReturn(true);

        assertThatThrownBy(() -> applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("optionCode 不能为空");
    }

    @Test
    void shouldRejectSubmitWithOptionCodeWhenItemHasNoOptions() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1"))
                .willReturn(true);
        given(ruleEngineService.requiresOption("item-1")).willReturn(false);

        assertThatThrownBy(() -> applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, "OPTION_A")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前项目不需要选择评分选项");
    }

    @Test
    void shouldAttachScoringSnapshotWhenSubmittingWithOptionCode() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1"))
                .willReturn(true);
        given(ruleEngineService.requiresOption("item-1")).willReturn(true);
        given(ruleEngineService.calculatePoints(eq("item-1"), eq("OPTION_A"), any(StudentContext.class)))
                .willReturn(new BigDecimal("2.00"));
        given(ruleEngineService.calculateMaxPoints(eq("item-1"), any(StudentContext.class)))
                .willReturn(new BigDecimal("6.00"));
        given(applicationSubmissionRepository.save(any(ApplicationSubmission.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ApplicationSubmissionView result = applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, "OPTION_A"));

        assertThat(result.getAppliedPoints()).isEqualByComparingTo("2.00");
        assertThat(result.getMaxPoints()).isEqualByComparingTo("6.00");
        assertThat(result.isExceedsMaxPoints()).isFalse();
        ArgumentCaptor<ApplicationSubmission> submissionCaptor = forClass(ApplicationSubmission.class);
        verify(applicationSubmissionRepository).save(submissionCaptor.capture());
        ApplicationScoringSnapshot snapshot = submissionCaptor.getValue().getScoringSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.optionCode()).isEqualTo("OPTION_A");
        assertThat(snapshot.evidenceCount()).isEqualTo(1);
    }

    @Test
    void shouldDeleteOwnedDraftApplication() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
        given(applicationOrgMembershipValidator.isActiveMember(1001L, 10L)).willReturn(true);
        given(applicationSubmissionRepository.save(any(ApplicationSubmission.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        applicationService.deleteOwnedApplication(new DeleteApplicationCommand(1L, 0L));

        ArgumentCaptor<ApplicationSubmission> submissionCaptor = forClass(ApplicationSubmission.class);
        verify(applicationSubmissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo(ApplicationSubmissionStatus.DELETED);
        assertThat(submissionCaptor.getValue().getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldRejectDeletingAnotherUsersApplication() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(new ApplicationSubmission(
                1L, 2002L, 10L, "competition", "item-1", "2025-2026", "1",
                "申请标题", "申请说明", List.of(sampleAttachment()), ApplicationSubmissionStatus.DRAFT,
                null, Instant.now(), Instant.now(), 0L
        )));

        assertThatThrownBy(() -> applicationService.deleteOwnedApplication(new DeleteApplicationCommand(1L, 0L)))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权操作该申请");
    }

    @Test
    void shouldDeclareTransactionalBoundaryOnWriteMethods() throws NoSuchMethodException {
        Method createMethod = ApplicationSubmissionCommandApplicationService.class.getMethod(
                "createDraft",
                CreateApplicationDraftCommand.class
        );
        Method submitMethod = ApplicationSubmissionCommandApplicationService.class.getMethod(
                "submit",
                SubmitApplicationCommand.class
        );
        Method deleteMethod = ApplicationSubmissionCommandApplicationService.class.getMethod(
                "deleteOwnedApplication",
                DeleteApplicationCommand.class
        );

        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(submitMethod.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(deleteMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private UserAuthorizationContext currentUser() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.create", "application.submit"),
                List.of()
        );
    }

    private AttachmentRef sampleAttachment() {
        return new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 10L, 1001L);
    }

    private ApplicationSubmission savedDraft() {
        return new ApplicationSubmission(
                1L,
                1001L,
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 10L, 1001L)),
                ApplicationSubmissionStatus.DRAFT,
                null,
                Instant.now(),
                Instant.now(),
                0L
        );
    }
}
