package edu.whut.eval.infra.cache;

import edu.whut.eval.domain.iam.model.IamUser;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("local")
public class NoopUserCacheGateway implements UserCacheGateway {

    @Override
    public Optional<IamUser> getByUserNo(String userNo) {
        return Optional.empty();
    }

    @Override
    public void put(IamUser user) {
    }

    @Override
    public void evictByUserNo(String userNo) {
    }
}
