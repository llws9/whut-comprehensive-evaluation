package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalRecordStateMachineTest {

    @Test
    void shouldSubmitDraftAndIncrementVersion() {
        FinalRecord draft = draftRecord();

        FinalRecord submitted = draft.submit(0L);

        assertThat(submitted.getStatus()).isEqualTo(FinalRecordStatus.SUBMITTED);
        assertThat(submitted.getVersion()).isEqualTo(1L);
        assertThat(submitted.getSubmittedAt()).isNotNull();
        assertThat(submitted.getCreatedAt()).isEqualTo(draft.getCreatedAt());
        assertThat(submitted.getUpdatedAt()).isAfterOrEqualTo(draft.getUpdatedAt());
        assertThat(draft.getStatus()).isEqualTo(FinalRecordStatus.DRAFT);
        assertThat(draft.getVersion()).isEqualTo(0L);
        assertThat(draft.getSubmittedAt()).isNull();
    }

    @Test
    void shouldConfirmSubmittedRecordWithOptionalComment() {
        FinalRecord submitted = draftRecord().submit(0L);

        FinalRecord confirmed = submitted.confirm(1L, "辅导员已复核，无异议");

        assertThat(confirmed.getStatus()).isEqualTo(FinalRecordStatus.CONFIRMED);
        assertThat(confirmed.getVersion()).isEqualTo(2L);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getConfirmComment()).isEqualTo("辅导员已复核，无异议");
        assertThat(submitted.getStatus()).isEqualTo(FinalRecordStatus.SUBMITTED);
        assertThat(submitted.getVersion()).isEqualTo(1L);
        assertThat(submitted.getConfirmedAt()).isNull();
    }

    @Test
    void shouldRejectInvalidTransitionsAndVersionMismatches() {
        FinalRecord draft = draftRecord();
        FinalRecord submitted = draft.submit(0L);
        FinalRecord confirmed = submitted.confirm(1L, null);

        assertThatThrownBy(() -> draft.confirm(0L, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> submitted.submit(1L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> confirmed.confirm(2L, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> draft.submit(9L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> submitted.confirm(9L, null)).isInstanceOf(ConflictException.class);
    }

    private FinalRecord draftRecord() {
        Instant now = Instant.parse("2026-07-07T12:00:00Z");
        return FinalRecord.createDraft(
                null,
                1001L,
                "2025-2026",
                new BigDecimal("0.80"),
                new BigDecimal("5.00"),
                new BigDecimal("0.60"),
                new BigDecimal("1.20"),
                new BigDecimal("7.60"),
                now
        );
    }
}
