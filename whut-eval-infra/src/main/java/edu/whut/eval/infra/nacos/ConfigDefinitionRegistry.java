package edu.whut.eval.infra.nacos;

import java.util.List;

public interface ConfigDefinitionRegistry {

    List<ConfigDefinition> getDefinitions();
}
