package edu.whut.eval.application.auth.service;

/**
 * 抽象密码摘要生成能力，避免应用层直接依赖具体摘要算法实现。
 */
public interface PasswordHasher {

    /**
     * 对原始密码进行摘要。
     */
    String hash(String rawPassword);
}
