package edu.whut.eval.infra.nacos.parser;

import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;

public interface ConfigPayloadParser {

    boolean supports(ConfigFormat format);

    <T> T parse(RawConfigPayload payload, Class<T> targetType);
}
