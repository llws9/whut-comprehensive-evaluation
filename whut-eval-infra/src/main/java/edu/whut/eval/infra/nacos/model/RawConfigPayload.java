package edu.whut.eval.infra.nacos.model;

import java.time.Instant;

public record RawConfigPayload(
        ConfigResource resource,
        String content,
        String source,
        Instant loadedAt
) {
}
