package edu.whut.eval.infra.cache;

import edu.whut.eval.application.preference.service.UserPreferenceCacheGateway;
import org.springframework.stereotype.Component;

/**
 * 用户偏好设置缓存网关的空实现。
 * 在真实缓存接入前，先用它固定写后缓存失效的调用点。
 */
@Component
public class NoopUserPreferenceCacheGateway implements UserPreferenceCacheGateway {

    @Override
    public void evictByUserId(Long userId) {
        // 预留给后续接入 Redis 或本地缓存时实现。
    }
}
