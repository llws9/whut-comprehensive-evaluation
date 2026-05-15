package edu.whut.eval.infra.nacos;

import edu.whut.eval.infra.nacos.model.ConfigResource;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;

import java.util.Optional;

public interface ConfigLoader {

    Optional<RawConfigPayload> load(ConfigResource resource);

    RawConfigPayload loadRequired(ConfigResource resource);
}
