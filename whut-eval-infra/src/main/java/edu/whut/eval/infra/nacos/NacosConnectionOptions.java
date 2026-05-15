package edu.whut.eval.infra.nacos;

public record NacosConnectionOptions(
        String serverAddress,
        String namespace,
        String username,
        String password
) {
}
