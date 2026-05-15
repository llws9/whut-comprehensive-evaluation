package edu.whut.eval.infra.cache;

import edu.whut.eval.domain.iam.model.IamUser;

import java.util.Optional;

public interface UserCacheGateway {

    Optional<IamUser> getByUserNo(String userNo);

    void put(IamUser user);

    void evictByUserNo(String userNo);
}
