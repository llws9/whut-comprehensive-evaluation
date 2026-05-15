package edu.whut.eval.infra.nacos;

import java.util.Optional;

public interface TypedConfigBindingRegistry {

    Optional<TypedConfigBinding<?>> find(String definitionName);
}
