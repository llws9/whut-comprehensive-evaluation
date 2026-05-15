package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.model.IamUserCredential;
import edu.whut.eval.domain.iam.query.UserPageQuery;
import edu.whut.eval.domain.shared.PageResult;

import java.util.Optional;

public interface IamUserQueryRepository {

    Optional<IamUser> findById(Long id);

    Optional<IamUser> findByUserNo(String userNo);

    Optional<IamUserCredential> findCredentialByUserNo(String userNo);

    PageResult<IamUser> pageUsers(UserPageQuery query);
}
