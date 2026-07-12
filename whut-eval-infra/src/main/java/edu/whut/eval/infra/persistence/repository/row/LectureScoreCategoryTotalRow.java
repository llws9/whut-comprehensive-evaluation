package edu.whut.eval.infra.persistence.repository.row;

import java.math.BigDecimal;

public class LectureScoreCategoryTotalRow {
    private String categoryCode;
    private BigDecimal scoreValue;

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public BigDecimal getScoreValue() {
        return scoreValue;
    }

    public void setScoreValue(BigDecimal scoreValue) {
        this.scoreValue = scoreValue;
    }
}
