package edu.whut.eval.infra.nacos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StaticTypedConfigBindingRegistry implements TypedConfigBindingRegistry {

    private final Map<String, TypedConfigBinding<?>> bindings;

    public StaticTypedConfigBindingRegistry(List<TypedConfigBinding<?>> bindings) {
        this.bindings = bindings == null ? Map.of() : bindings.stream()
                .collect(Collectors.toUnmodifiableMap(TypedConfigBinding::definitionName, Function.identity()));
    }

    @Override
    public Optional<TypedConfigBinding<?>> find(String definitionName) {
        return Optional.ofNullable(bindings.get(definitionName));
    }
}
