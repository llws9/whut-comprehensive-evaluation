package edu.whut.eval.app.application;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationSubmissionStateMachineTest {

    @Test
    void shouldAllowUpdateSubmitAndWithdrawFromDraft() {
        ApplicationSubmission draft = draft();

        ApplicationSubmission updated = draft.updateDraft("新标题", "新说明", draft.getEvidenceAttachments(), 0L);
        ApplicationSubmission submitted = updated.submit(1L);

        assertThat(updated.getStatus()).isEqualTo(ApplicationSubmissionStatus.DRAFT);
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(submitted.getStatus()).isEqualTo(ApplicationSubmissionStatus.SUBMITTED);
        assertThat(submitted.getVersion()).isEqualTo(2L);
    }

    @Test
    void shouldRejectUpdateWhenStatusNotEditable() {
        ApplicationSubmission submitted = draft().submit(0L);

        assertThatThrownBy(() -> submitted.updateDraft("新标题", "新说明", submitted.getEvidenceAttachments(), 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前申请状态不允许学生编辑");
    }

    @Test
    void shouldRejectSubmitWhenNoAttachments() {
        ApplicationSubmission draft = ApplicationSubmission.createDraft(
                1001L,
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of()
        );

        assertThatThrownBy(() -> draft.submit(0L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("申请附件不能为空");
    }

    @Test
    void shouldAllowWithdrawFromDraft() {
        ApplicationSubmission withdrawn = draft().withdraw(0L);

        assertThat(withdrawn.getStatus()).isEqualTo(ApplicationSubmissionStatus.WITHDRAWN);
        assertThat(withdrawn.getVersion()).isEqualTo(1L);
    }

    private ApplicationSubmission draft() {
        return ApplicationSubmission.createDraft(
                1001L,
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 10L, 1001L))
        );
    }
}
