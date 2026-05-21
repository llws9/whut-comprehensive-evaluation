package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamUser;

public interface UserAdminCommandRepository {

    IamUser create(String userNo,
                   String userName,
                   String email,
                   String phone,
                   String passwordHash,
                   String status);

    IamUser updateProfile(Long userId,
                          String userName,
                          String email,
                          String phone,
                          String passwordHash);

    boolean updateStatus(Long userId, String expectedCurrentStatus, String targetStatus);
}
