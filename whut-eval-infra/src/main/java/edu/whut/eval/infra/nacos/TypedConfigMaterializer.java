package edu.whut.eval.infra.nacos;

import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import edu.whut.eval.infra.nacos.parser.ConfigPayloadParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TypedConfigMaterializer {

    private static final Logger log = LoggerFactory.getLogger(TypedConfigMaterializer.class);

    private final TypedConfigBindingRegistry bindingRegistry;
    private final TypedConfigRepository typedConfigRepository;
    private final List<ConfigPayloadParser> parsers;

    public TypedConfigMaterializer(TypedConfigBindingRegistry bindingRegistry,
                                   TypedConfigRepository typedConfigRepository,
                                   List<ConfigPayloadParser> parsers) {
        this.bindingRegistry = bindingRegistry;
        this.typedConfigRepository = typedConfigRepository;
        this.parsers = parsers;
    }

    public void materialize(String definitionName, RawConfigPayload payload) {
        bindingRegistry.find(definitionName).ifPresent(binding -> saveParsedObject(binding, payload));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void saveParsedObject(TypedConfigBinding binding, RawConfigPayload payload) {
        ConfigPayloadParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(payload.resource().format()))
                .findFirst()
                .orElseThrow(() -> new ConfigLoadException("No parser found for config format " + payload.resource().format()));
        Object parsed = parser.parse(payload, binding.targetType());
        typedConfigRepository.save(binding.definitionName(), parsed);
        AppLog.info(log, "nacos.config.typed.materialized",
                "definition", binding.definitionName(),
                "targetType", binding.targetType().getSimpleName(),
                "dataId", payload.resource().dataId());
    }
}
