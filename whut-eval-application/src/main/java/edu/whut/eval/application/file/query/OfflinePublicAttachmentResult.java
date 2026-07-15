package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class OfflinePublicAttachmentResult {

    private final Long entryId;
    private final String status;
    private final LocalDateTime offlineAt;

    public OfflinePublicAttachmentResult(Long entryId, String status, LocalDateTime offlineAt) {
        this.entryId = entryId;
        this.status = status;
        this.offlineAt = offlineAt;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getOfflineAt() {
        return offlineAt;
    }
}
