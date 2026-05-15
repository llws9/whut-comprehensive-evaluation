package edu.whut.eval.domain.iam.model;

import java.io.Serializable;

public class IamUserCredential implements Serializable {

    private final Long id;
    private final String userNo;
    private final String userName;
    private final String passwordHash;
    private final String status;

    public IamUserCredential(Long id,
                             String userNo,
                             String userName,
                             String passwordHash,
                             String status) {
        this.id = id;
        this.userNo = userNo;
        this.userName = userName;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getUserName() {
        return userName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }
}
