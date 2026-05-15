package edu.whut.eval.infra.nacos;

public record TypedConfigBinding<T>(
        String definitionName,
        Class<T> targetType
) {

    public TypedConfigBinding {
        if (definitionName == null || definitionName.isBlank()) {
            throw new IllegalArgumentException("definitionName must not be blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
    }
}
