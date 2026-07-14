package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.LectureImportBatchLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MySqlLectureImportBatchLock implements LectureImportBatchLock {

    private static final String PREFIX = "D8_LECTURE:";

    private final JdbcTemplate jdbcTemplate;

    public MySqlLectureImportBatchLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(String lectureBatchId, Duration timeout) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName(lectureBatchId),
                Math.toIntExact(timeout.toSeconds())
        );
        return result != null && result == 1;
    }

    @Override
    public void release(String lectureBatchId) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName(lectureBatchId));
    }

    private String lockName(String lectureBatchId) {
        return PREFIX + lectureBatchId;
    }
}
