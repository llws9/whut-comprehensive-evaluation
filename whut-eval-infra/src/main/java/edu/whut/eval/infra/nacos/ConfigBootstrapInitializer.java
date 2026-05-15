package edu.whut.eval.infra.nacos;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.exception.NacosBootstrapException;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Optional;

public class ConfigBootstrapInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrapInitializer.class);

    private final ConfigDefinitionRegistry definitionRegistry;
    private final ConfigLoader configLoader;
    private final ConfigSnapshotRepository snapshotRepository;
    private final ConfigSubscriber configSubscriber;
    private final TypedConfigMaterializer typedConfigMaterializer;

    public ConfigBootstrapInitializer(ConfigDefinitionRegistry definitionRegistry,
                                      ConfigLoader configLoader,
                                      ConfigSnapshotRepository snapshotRepository,
                                      ConfigSubscriber configSubscriber,
                                      TypedConfigMaterializer typedConfigMaterializer) {
        this.definitionRegistry = definitionRegistry;
        this.configLoader = configLoader;
        this.snapshotRepository = snapshotRepository;
        this.configSubscriber = configSubscriber;
        this.typedConfigMaterializer = typedConfigMaterializer;
    }

    @Override
    public void afterPropertiesSet() {
        List<ConfigDefinition> definitions = definitionRegistry.getDefinitions();
        if (definitions.isEmpty()) {
            throw new NacosBootstrapException("No ConfigDefinition registered for nacos bootstrap", null);
        }

        try {
            for (ConfigDefinition definition : definitions) {
                RawConfigPayload payload = loadAtStartup(definition);
                if (payload != null) {
                    snapshotRepository.save(definition.name(), payload);
                    typedConfigMaterializer.materialize(definition.name(), payload);
                }
                if (definition.autoRefresh()) {
                    configSubscriber.subscribe(definition, (changedDefinition, changedPayload) -> {
                        snapshotRepository.save(changedDefinition.name(), changedPayload);
                        typedConfigMaterializer.materialize(changedDefinition.name(), changedPayload);
                        AppLog.info(log, "nacos.config.refreshed",
                                "definition", changedDefinition.name(),
                                "dataId", changedDefinition.resource().dataId(),
                                "group", changedDefinition.resource().group());
                    });
                }
            }
            AppLog.info(log, "nacos.bootstrap.completed", "definitionCount", definitions.size());
        } catch (NacosBootstrapException exception) {
            AppLog.error(log, exception, "nacos.bootstrap.failed");
            throw exception;
        } catch (RuntimeException exception) {
            AppLog.error(log, exception, "nacos.bootstrap.failed");
            throw new NacosBootstrapException("Failed to bootstrap required nacos configuration", exception);
        }
    }

    private RawConfigPayload loadAtStartup(ConfigDefinition definition) {
        if (definition.required()) {
            RawConfigPayload payload = configLoader.loadRequired(definition.resource());
            AppLog.info(log, "nacos.config.preloaded",
                    "definition", definition.name(),
                    "dataId", definition.resource().dataId(),
                    "group", definition.resource().group());
            return payload;
        }
        Optional<RawConfigPayload> payload = configLoader.load(definition.resource());
        payload.ifPresent(value -> AppLog.info(log, "nacos.config.preloaded.optional",
                "definition", definition.name(),
                "dataId", definition.resource().dataId(),
                "group", definition.resource().group()));
        return payload.orElse(null);
    }
}
