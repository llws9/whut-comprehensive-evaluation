package edu.whut.eval.infra.nacos;

import edu.whut.eval.infra.nacos.model.RawConfigPayload;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConfigSnapshotRepository implements ConfigSnapshotRepository {

    private final ConcurrentHashMap<String, RawConfigPayload> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(String definitionName, RawConfigPayload payload) {
        snapshots.put(definitionName, payload);
    }

    @Override
    public Optional<RawConfigPayload> find(String definitionName) {
        return Optional.ofNullable(snapshots.get(definitionName));
    }

    @Override
    public Map<String, RawConfigPayload> findAll() {
        return Map.copyOf(snapshots);
    }
}
