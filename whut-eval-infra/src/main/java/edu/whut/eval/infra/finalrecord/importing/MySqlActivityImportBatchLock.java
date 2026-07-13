package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.ActivityImportBatchLock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MySqlActivityImportBatchLock implements ActivityImportBatchLock {

    private static final String PREFIX = "D9_ACTIVITY:";

    private final JdbcTemplate jdbcTemplate;

    public MySqlActivityImportBatchLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(String activityBatchId, Duration timeout) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName(activityBatchId),
                Math.toIntExact(timeout.toSeconds())
        );
        if (result == null) {
            throw new DataAccessResourceFailureException("GET_LOCK returned NULL for activity import batch");
        }
        return result == 1;
    }

    @Override
    public void release(String activityBatchId) {
        Integer result = jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName(activityBatchId));
        if (result == null || result != 1) {
            throw new DataAccessResourceFailureException("RELEASE_LOCK did not release activity import batch");
        }
    }

    private String lockName(String activityBatchId) {
        return PREFIX + activityBatchId;
    }
}
