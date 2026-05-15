package edu.whut.eval.infra.nacos;

public interface ConfigSubscription extends AutoCloseable {

    ConfigDefinition definition();

    @Override
    void close();
}
