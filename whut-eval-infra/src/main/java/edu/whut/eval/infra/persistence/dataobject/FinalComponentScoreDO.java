package edu.whut.eval.infra.persistence.dataobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinalComponentScoreDO {
    private Long id;
    private Long finalRecordId;
    private String categoryCode;
    private String itemCode;
    private BigDecimal scoreValue;
    private String displayText;
    private String sourceType;
    private String sourceRefId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFinalRecordId() { return finalRecordId; }
    public void setFinalRecordId(Long finalRecordId) { this.finalRecordId = finalRecordId; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public BigDecimal getScoreValue() { return scoreValue; }
    public void setScoreValue(BigDecimal scoreValue) { this.scoreValue = scoreValue; }
    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRefId() { return sourceRefId; }
    public void setSourceRefId(String sourceRefId) { this.sourceRefId = sourceRefId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
