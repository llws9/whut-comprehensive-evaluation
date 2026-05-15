package edu.whut.eval.app.application;

import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationSubmissionCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
    private final ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy = mock(ApplicationSubmissionWindowPolicy.class);
    private final ActiveSubmissionPolicy activeSubmissionPolicy = mock(ActiveSubmissionPolicy.class);
    private final ApplicationAttachmentResolver applicationAttachmentResolver = mock(ApplicationAttachmentResolver.class);

    private final ApplicationSubmissionCommandApplicationService applicationService =
            new ApplicationSubmissionCommandApplicationService(
                    userAuthorizationContextAssembler,
                    applicationSubmissionRepository,
                    applicationSubmissionWindowPolicy,
                    activeSubmissionPolicy,
                    applicationAttachmentResolver
            );

    @Test
    void shouldCreateDraftForCurrentUser() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
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
    void shouldDeclareTransactionalBoundaryOnWriteMethods() throws NoSuchMethodException {
        Method createMethod = ApplicationSubmissionCommandApplicationService.class.getMethod(
                "createDraft",
                CreateApplicationDraftCommand.class
        );
        Method submitMethod = ApplicationSubmissionCommandApplicationService.class.getMethod(
                "submit",
                SubmitApplicationCommand.class
        );

        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(submitMethod.isAnnotationPresent(Transactional.class)).isTrue();
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
