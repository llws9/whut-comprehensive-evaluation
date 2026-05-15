package edu.whut.eval.infra.cache;

public final class CacheKeyBuilder {

    private CacheKeyBuilder() {
    }

    public static String iamUserByUserNo(String userNo) {
        return "iam:user:userNo:" + userNo;
    }
}
