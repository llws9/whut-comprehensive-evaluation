package edu.whut.eval.infra.nacos;

import edu.whut.eval.infra.nacos.model.RawConfigPayload;

import java.util.Map;
import java.util.Optional;

public interface ConfigSnapshotRepository {

    void save(String definitionName, RawConfigPayload payload);

    Optional<RawConfigPayload> find(String definitionName);

    Map<String, RawConfigPayload> findAll();
}
