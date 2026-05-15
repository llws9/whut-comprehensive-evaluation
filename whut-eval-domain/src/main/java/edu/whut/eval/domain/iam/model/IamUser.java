package edu.whut.eval.domain.iam.model;

import java.io.Serializable;

public record IamUser(
        Long id,
        String userNo,
        String userName,
        String email,
        String phone,
        String status
) implements Serializable {
}
