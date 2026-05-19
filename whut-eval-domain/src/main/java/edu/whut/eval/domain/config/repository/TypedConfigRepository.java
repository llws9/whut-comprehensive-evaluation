package edu.whut.eval.domain.config.repository;

import java.util.Optional;

public interface TypedConfigRepository {

    <T> void save(String definitionName, T configObject);

    <T> Optional<T> find(String definitionName, Class<T> targetType);
}
