package edu.whut.eval.infra.nacos;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTypedConfigRepository implements TypedConfigRepository {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @Override
    public <T> void save(String definitionName, T configObject) {
        store.put(definitionName, configObject);
    }

    @Override
    public <T> Optional<T> find(String definitionName, Class<T> targetType) {
        Object value = store.get(definitionName);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(targetType.cast(value));
    }
}
