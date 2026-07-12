package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubmittedFinalRecordQueryTest {

    @Test
    void shouldNormalizeValidAcademicYearAndPagination() {
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(" 2025-2026 ", null, null, -1, 200);
        UnsubmittedFinalRecordQuery zeroPage = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 0, 20);

        assertThat(query.getAcademicYear()).isEqualTo("2025-2026");
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(100);
        assertThat(query.getOffset()).isZero();
        assertThat(zeroPage.getPageNo()).isEqualTo(1);
        assertThat(zeroPage.getOffset()).isZero();
    }

    @Test
    void shouldRejectInvalidAcademicYear() {
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery(null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("   ", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("abc", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2027", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2026-2025", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldNormalizePageSizeLikeExistingFinalRecordPageQuery() {
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 0).getPageSize()).isEqualTo(20);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, -1).getPageSize()).isEqualTo(20);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100).getPageSize()).isEqualTo(100);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 101).getPageSize()).isEqualTo(100);
    }

    @Test
    void shouldRejectOffsetOverflow() {
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, null, Long.MAX_VALUE, 100))
                .isInstanceOf(ValidationException.class)
                .hasMessage("pageNo 不合法");
    }

    @Test
    void shouldNormalizeGradeAndClassesWithoutCaseFoldingOrCommaSplitting() {
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(
                "2025-2026",
                " CS2022 ",
                List.of(" CS2201 ", "", "CS2201", "Class,With,Comma", "class"),
                2,
                20
        );

        assertThat(query.getGrade()).isEqualTo("CS2022");
        assertThat(query.getClasses()).containsExactly("CS2201", "Class,With,Comma", "class");
        assertThat(query.isClassesEmpty()).isFalse();
        assertThat(query.getOffset()).isEqualTo(20);
    }

    @Test
    void shouldTreatBlankClassesAsEmptyFilter() {
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(
                "2025-2026",
                " ",
                List.of(" ", ""),
                1,
                20
        );

        assertThat(query.getGrade()).isNull();
        assertThat(query.getClasses()).isEmpty();
        assertThat(query.isClassesEmpty()).isTrue();

        UnsubmittedFinalRecordQuery nullClasses = new UnsubmittedFinalRecordQuery(
                "2025-2026",
                null,
                null,
                1,
                20
        );
        assertThat(nullClasses.getClasses()).isEmpty();
        assertThat(nullClasses.isClassesEmpty()).isTrue();
    }

    @Test
    void shouldAllowExactlyFiveHundredNormalizedClasses() {
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            classes.add("CS" + i);
        }

        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20);

        assertThat(query.getClasses()).hasSize(500);
        assertThat(query.isClassesEmpty()).isFalse();
    }

    @Test
    void shouldRejectMoreThanFiveHundredNormalizedClasses() {
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            classes.add("CS" + i);
        }

        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("classes 不合法");
    }

    @Test
    void shouldApplyClassLimitAfterTrimBlankAndDeduplication() {
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            classes.add("CS2201");
        }

        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20);

        assertThat(query.getClasses()).containsExactly("CS2201");
    }

    @Test
    void shouldRejectOverlongGradeAndClassValues() {
        String overlong = "A".repeat(257);

        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", overlong, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("grade 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, List.of(overlong), 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("classes 不合法");
    }
}
