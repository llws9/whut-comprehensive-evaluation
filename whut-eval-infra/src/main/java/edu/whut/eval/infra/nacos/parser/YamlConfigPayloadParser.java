package edu.whut.eval.infra.nacos.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;

public class YamlConfigPayloadParser implements ConfigPayloadParser {

    private final ObjectMapper objectMapper;

    public YamlConfigPayloadParser() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public boolean supports(ConfigFormat format) {
        return ConfigFormat.YAML == format;
    }

    @Override
    public <T> T parse(RawConfigPayload payload, Class<T> targetType) {
        try {
            return objectMapper.readValue(payload.content(), targetType);
        } catch (Exception exception) {
            throw new ConfigLoadException("Failed to parse YAML config, dataId="
                    + payload.resource().dataId() + ", targetType=" + targetType.getSimpleName(), exception);
        }
    }
}
