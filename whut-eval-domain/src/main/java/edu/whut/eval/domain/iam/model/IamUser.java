package edu.whut.eval.domain.iam.model;

import java.io.Serializable;

public record IamUser(
        Long id,
        String userNo,
        String userName,
        String email,
        String phone,
        String status,
        String createdAt
) implements Serializable {
    public IamUser(Long id, String userNo, String userName, String email, String phone, String status) {
        this(id, userNo, userName, email, phone, status, null);
    }
}
