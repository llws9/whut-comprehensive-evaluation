package edu.whut.eval.application.finalrecord.importing;

import java.time.Duration;

public interface ActivityImportBatchLock {
    boolean tryAcquire(String activityBatchId, Duration timeout);

    void release(String activityBatchId);
}
