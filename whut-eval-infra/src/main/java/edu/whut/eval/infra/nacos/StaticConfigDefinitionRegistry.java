package edu.whut.eval.infra.nacos;

import java.util.Comparator;
import java.util.List;

public class StaticConfigDefinitionRegistry implements ConfigDefinitionRegistry {

    private final List<ConfigDefinition> definitions;

    public StaticConfigDefinitionRegistry(List<ConfigDefinition> definitions) {
        this.definitions = definitions == null ? List.of() : definitions.stream()
                .sorted(Comparator.comparing(ConfigDefinition::name))
                .toList();
    }

    @Override
    public List<ConfigDefinition> getDefinitions() {
        return definitions;
    }
}
