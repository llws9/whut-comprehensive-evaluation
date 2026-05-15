package edu.whut.eval.infra.nacos.exception;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.ConfigDefinition;

public class NacosConfigSubscribeException extends ConfigLoadException {

    public NacosConfigSubscribeException(ConfigDefinition definition, Throwable cause) {
        super(buildMessage(definition, cause == null ? "unknown nacos subscribe error" : cause.getMessage()), cause);
    }

    private static String buildMessage(ConfigDefinition definition, String reason) {
        return "Nacos config subscribe failed, definition=%s, dataId=%s, group=%s, reason=%s"
                .formatted(definition.name(), definition.resource().dataId(), definition.resource().group(), reason);
    }
}
