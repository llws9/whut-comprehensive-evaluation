package edu.whut.eval.application.preference.service;

/**
 * 用户偏好设置缓存网关。
 * 当前先提供最小抽象，用于固定写后缓存失效的调用位置。
 */
public interface UserPreferenceCacheGateway {

    /**
     * 在偏好设置写入成功后失效当前用户缓存。
     */
    void evictByUserId(Long userId);
}
