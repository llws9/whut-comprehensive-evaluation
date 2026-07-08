package edu.whut.eval.domain.finalrecord.model;

import java.math.BigDecimal;
import java.time.Instant;

public class FinalComponentScore {

    private final Long id;
    private final Long finalRecordId;
    private final String categoryCode;
    private final String itemCode;
    private final BigDecimal scoreValue;
    private final String displayText;
    private final String sourceType;
    private final String sourceRefId;
    private final Instant createdAt;

    public FinalComponentScore(Long id,
                               Long finalRecordId,
                               String categoryCode,
                               String itemCode,
                               BigDecimal scoreValue,
                               String displayText,
                               String sourceType,
                               String sourceRefId,
                               Instant createdAt) {
        this.id = id;
        this.finalRecordId = finalRecordId;
        this.categoryCode = categoryCode;
        this.itemCode = itemCode;
        this.scoreValue = scoreValue;
        this.displayText = displayText;
        this.sourceType = sourceType;
        this.sourceRefId = sourceRefId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getFinalRecordId() {
        return finalRecordId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public BigDecimal getScoreValue() {
        return scoreValue;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRefId() {
        return sourceRefId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
