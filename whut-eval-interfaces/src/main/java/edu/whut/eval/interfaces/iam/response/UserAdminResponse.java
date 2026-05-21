package edu.whut.eval.interfaces.iam.response;

public class UserAdminResponse {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String status;

    public UserAdminResponse(Long userId, String userNo, String userName, String status) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.status = status;
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
}
