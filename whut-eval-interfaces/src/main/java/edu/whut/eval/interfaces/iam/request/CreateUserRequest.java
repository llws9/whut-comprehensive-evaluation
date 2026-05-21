package edu.whut.eval.interfaces.iam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class CreateUserRequest {

    @NotBlank
    private String userNo;

    @NotBlank
    private String userName;

    @NotBlank
    private String password;

    private String email;

    private String phone;

    @Positive
    private Long primaryOrgUnitId;

    public String getUserNo() {
        return userNo;
    }

    public void setUserNo(String userNo) {
        this.userNo = userNo;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getPrimaryOrgUnitId() {
        return primaryOrgUnitId;
    }

    public void setPrimaryOrgUnitId(Long primaryOrgUnitId) {
        this.primaryOrgUnitId = primaryOrgUnitId;
    }
}
