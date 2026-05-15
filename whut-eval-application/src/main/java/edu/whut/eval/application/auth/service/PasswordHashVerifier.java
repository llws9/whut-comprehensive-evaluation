package edu.whut.eval.application.auth.service;

/**
 * 抽象密码摘要校验能力，便于后续替换摘要算法而不影响登录流程编排。
 */
public interface PasswordHashVerifier {

    /**
     * 校验原始密码与持久化摘要是否匹配。
     */
    boolean matches(String rawPassword, String storedPasswordHash);
}
