package edu.whut.eval.common.error;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_ERROR("VAL-4001", 400, "请求参数不合法"),
    AUTHENTICATION_FAILED("AUTH-4010", 401, "认证失败"),
    TOKEN_EXPIRED("AUTH-4011", 401, "令牌已过期"),
    TOKEN_INVALID("AUTH-4012", 401, "令牌非法"),
    ACCESS_DENIED("AUTH-4030", 403, "无权限访问"),
    RESOURCE_NOT_FOUND("RES-4040", 404, "资源不存在"),
    RESOURCE_CONFLICT("BIZ-4090", 409, "资源状态冲突"),
    BIZ_RULE_VIOLATION("BIZ-4091", 409, "业务规则不满足"),
    NACOS_CONFIG_LOAD_FAILED("CFG-5031", 503, "Nacos 配置加载失败"),
    REDIS_ACCESS_FAILED("EXT-5032", 503, "缓存访问失败"),
    FILE_STORAGE_FAILED("EXT-5033", 503, "对象存储访问失败"),
    DB_QUERY_FAILED("DB-5001", 500, "数据库查询失败"),
    DB_WRITE_FAILED("DB-5002", 500, "数据库写入失败"),
    SYSTEM_ERROR("SYS-5000", 500, "系统内部错误");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    CommonErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
