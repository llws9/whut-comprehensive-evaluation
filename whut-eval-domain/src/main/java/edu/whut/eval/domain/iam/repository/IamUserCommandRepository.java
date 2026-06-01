package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamUser;

public interface IamUserCommandRepository {

    IamUser createUser(String userNo, String userName, String passwordHash, String email, String phone);

    boolean updateForImportByUserNo(String userNo, String userName, String passwordHash, String email, String phone);

    boolean updateStatus(Long userId, String status);
}