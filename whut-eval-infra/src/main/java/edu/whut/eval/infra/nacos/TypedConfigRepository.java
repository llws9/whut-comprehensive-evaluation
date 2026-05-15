package edu.whut.eval.infra.nacos;

import java.util.Optional;

public interface TypedConfigRepository {

    <T> void save(String definitionName, T configObject);

    <T> Optional<T> find(String definitionName, Class<T> targetType);
}
