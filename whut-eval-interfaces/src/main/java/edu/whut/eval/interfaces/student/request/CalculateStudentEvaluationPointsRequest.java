package edu.whut.eval.interfaces.student.request;

import jakarta.validation.constraints.NotBlank;

public class CalculateStudentEvaluationPointsRequest {

    @NotBlank(message = "itemCode 不能为空")
    private String itemCode;

    @NotBlank(message = "optionCode 不能为空")
    private String optionCode;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getOptionCode() {
        return optionCode;
    }

    public void setOptionCode(String optionCode) {
        this.optionCode = optionCode;
    }
}
