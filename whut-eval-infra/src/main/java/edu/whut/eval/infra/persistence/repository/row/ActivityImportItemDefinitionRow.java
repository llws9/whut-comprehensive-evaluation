package edu.whut.eval.infra.persistence.repository.row;

public class ActivityImportItemDefinitionRow {
    private String itemCode;
    private String categoryCode;
    private String capRuleJson;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCapRuleJson() {
        return capRuleJson;
    }

    public void setCapRuleJson(String capRuleJson) {
        this.capRuleJson = capRuleJson;
    }
}
