package edu.whut.eval.infra.nacos;

public interface ConfigSubscriber {

    ConfigSubscription subscribe(ConfigDefinition definition, ConfigChangeHandler handler);
}
