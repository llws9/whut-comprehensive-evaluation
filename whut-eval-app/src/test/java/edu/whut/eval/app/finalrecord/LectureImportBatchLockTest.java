package edu.whut.eval.app.finalrecord;

import edu.whut.eval.infra.finalrecord.importing.MySqlLectureImportBatchLock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LectureImportBatchLockTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MySqlLectureImportBatchLock lock = new MySqlLectureImportBatchLock(jdbcTemplate);

    @Test
    void shouldAcquireAndReleaseNamedLock() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), eq("D8_LECTURE:batch"), eq(30)))
                .willReturn(1);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isTrue();

        lock.release("batch");
        verify(jdbcTemplate).queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D8_LECTURE:batch");
    }

    @Test
    void shouldReturnFalseWhenNamedLockTimesOut() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), eq("D8_LECTURE:batch"), eq(30)))
                .willReturn(0);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNamedLockReturnsNull() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), eq("D8_LECTURE:batch"), eq(30)))
                .willReturn(null);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
    }
}
