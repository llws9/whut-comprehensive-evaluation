package edu.whut.eval.infra.nacos.exception;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.model.ConfigResource;

public class NacosConfigLoadException extends ConfigLoadException {

    public NacosConfigLoadException(ConfigResource resource, Throwable cause) {
        super(buildMessage(resource, cause == null ? "unknown nacos error" : cause.getMessage()), cause);
    }

    public NacosConfigLoadException(ConfigResource resource, String reason) {
        super(buildMessage(resource, reason));
    }

    private static String buildMessage(ConfigResource resource, String reason) {
        return "Nacos config load failed, dataId=%s, group=%s, format=%s, reason=%s"
                .formatted(resource.dataId(), resource.group(), resource.format(), reason);
    }
}
