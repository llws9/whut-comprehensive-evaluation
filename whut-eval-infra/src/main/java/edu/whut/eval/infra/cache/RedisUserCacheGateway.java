package edu.whut.eval.infra.cache;

import edu.whut.eval.domain.iam.model.IamUser;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisUserCacheGateway implements UserCacheGateway {

    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUserCacheGateway(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<IamUser> getByUserNo(String userNo) {
        Object value = redisTemplate.opsForValue().get(CacheKeyBuilder.iamUserByUserNo(userNo));
        if (value instanceof IamUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public void put(IamUser user) {
        redisTemplate.opsForValue().set(CacheKeyBuilder.iamUserByUserNo(user.userNo()), user, USER_CACHE_TTL);
    }

    @Override
    public void evictByUserNo(String userNo) {
        redisTemplate.delete(CacheKeyBuilder.iamUserByUserNo(userNo));
    }
}
