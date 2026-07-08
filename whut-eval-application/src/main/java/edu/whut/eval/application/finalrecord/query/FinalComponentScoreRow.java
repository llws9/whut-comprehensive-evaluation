package edu.whut.eval.application.finalrecord.query;

import java.math.BigDecimal;
import java.time.Instant;

public class FinalComponentScoreRow {
    private Long id;
    private Long finalRecordId;
    private String categoryCode;
    private String itemCode;
    private String itemName;
    private BigDecimal scoreValue;
    private String displayText;
    private String sourceType;
    private String sourceRefId;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFinalRecordId() { return finalRecordId; }
    public void setFinalRecordId(Long finalRecordId) { this.finalRecordId = finalRecordId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public BigDecimal getScoreValue() { return scoreValue; }
    public void setScoreValue(BigDecimal scoreValue) { this.scoreValue = scoreValue; }
    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRefId() { return sourceRefId; }
    public void setSourceRefId(String sourceRefId) { this.sourceRefId = sourceRefId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
