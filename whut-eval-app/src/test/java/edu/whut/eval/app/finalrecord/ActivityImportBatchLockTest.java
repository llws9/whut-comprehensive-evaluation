package edu.whut.eval.app.finalrecord;

import edu.whut.eval.infra.finalrecord.importing.MySqlActivityImportBatchLock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActivityImportBatchLockTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MySqlActivityImportBatchLock lock = new MySqlActivityImportBatchLock(jdbcTemplate);

    @Test
    void shouldAcquireAndReleaseSameNamedLock() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
                eq("D9_ACTIVITY:batch"), eq(30))).willReturn(1);
        given(jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch"))
                .willReturn(1);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isTrue();

        lock.release("batch");
        verify(jdbcTemplate).queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch");
    }

    @Test
    void shouldReturnFalseWhenNamedLockTimesOut() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
                eq("D9_ACTIVITY:batch"), eq(30))).willReturn(0);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldThrowDataAccessExceptionWhenNamedLockReturnsNull() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
                eq("D9_ACTIVITY:batch"), eq(30))).willReturn(null);

        assertThatThrownBy(() -> lock.tryAcquire("batch", Duration.ofSeconds(30)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("GET_LOCK returned NULL");
    }

    @Test
    void shouldThrowDataAccessExceptionWhenReleaseReturnsZeroOrNull() {
        given(jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch"))
                .willReturn(0, (Integer) null);

        assertThatThrownBy(() -> lock.release("batch"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("RELEASE_LOCK did not release activity import batch");
        assertThatThrownBy(() -> lock.release("batch"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("RELEASE_LOCK did not release activity import batch");
    }
}
