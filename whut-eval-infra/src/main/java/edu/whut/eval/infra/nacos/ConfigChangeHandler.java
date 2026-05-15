package edu.whut.eval.infra.nacos;

import edu.whut.eval.infra.nacos.model.RawConfigPayload;

@FunctionalInterface
public interface ConfigChangeHandler {

    void onChange(ConfigDefinition definition, RawConfigPayload payload);
}
