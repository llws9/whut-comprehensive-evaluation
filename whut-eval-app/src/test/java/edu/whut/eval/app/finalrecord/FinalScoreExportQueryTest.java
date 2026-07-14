package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalScoreExportQueryTest {

    @Test
    void shouldNormalizeValidQuery() {
        FinalScoreExportQuery query = new FinalScoreExportQuery(
                " 2025-2026 ",
                " ",
                " CS2022 ",
                List.of("CS2201, CS2202", "CS2202", "CS2203,, ")
        );

        assertThat(query.academicYear()).isEqualTo("2025-2026");
        assertThat(query.status()).isNull();
        assertThat(query.grade()).isEqualTo("CS2022");
        assertThat(query.classes()).containsExactly("CS2201", "CS2202", "CS2203");
    }

    @Test
    void shouldRejectInvalidAcademicYearBeforeOtherSemanticErrors() {
        List<String> tooManyClasses = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            tooManyClasses.add("C" + index);
        }

        assertThatThrownBy(() -> new FinalScoreExportQuery("2025/2026", "draft", "CS2022", tooManyClasses))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectMissingOrBlankAcademicYear() {
        assertThatThrownBy(() -> new FinalScoreExportQuery(null, null, null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new FinalScoreExportQuery("   ", null, null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectNonConsecutiveAcademicYear() {
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2027", null, null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectLowercaseAndDraftStatus() {
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", "submitted", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 SUBMITTED 或 CONFIRMED");
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", "DRAFT", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 SUBMITTED 或 CONFIRMED");
    }

    @Test
    void shouldTreatBlankClassesAsAbsentAndReturnImmutableList() {
        FinalScoreExportQuery missing = new FinalScoreExportQuery("2025-2026", null, null, null);
        FinalScoreExportQuery blank = new FinalScoreExportQuery("2025-2026", null, null, List.of(",,", " "));

        assertThat(blank.classes()).isEmpty();
        assertThat(blank.classes()).isEqualTo(missing.classes());
        assertThatThrownBy(() -> blank.classes().add("CS2201"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAcceptFiveHundredNormalizedClassTokens() {
        List<String> raw = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            raw.add("CS" + index);
        }

        FinalScoreExportQuery query = new FinalScoreExportQuery("2025-2026", null, null, raw);

        assertThat(query.classes()).hasSize(500);
    }

    @Test
    void shouldRejectMoreThanFiveHundredNormalizedClassTokens() {
        List<String> raw = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            raw.add("CS" + index);
        }

        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", null, null, raw))
                .isInstanceOf(ValidationException.class)
                .hasMessage("classes 参数过多");
    }
}
