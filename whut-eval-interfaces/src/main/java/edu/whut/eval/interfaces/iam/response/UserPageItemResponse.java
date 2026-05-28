package edu.whut.eval.interfaces.iam.response;

import java.util.List;

public class UserPageItemResponse {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String status;
    private final List<String> orgUnits;
    private final List<String> roles;
    private final String createdAt;

    public UserPageItemResponse(Long userId,
                                String userNo,
                                String userName,
                                String status,
                                List<String> orgUnits,
                                List<String> roles,
                                String createdAt) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.status = status;
        this.orgUnits = orgUnits;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getUserName() {
        return userName;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getOrgUnits() {
        return orgUnits;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}