package edu.whut.eval.application.finalrecord.importing;

import java.time.Duration;

public interface LectureImportBatchLock {
    boolean tryAcquire(String lectureBatchId, Duration timeout);

    void release(String lectureBatchId);
}
