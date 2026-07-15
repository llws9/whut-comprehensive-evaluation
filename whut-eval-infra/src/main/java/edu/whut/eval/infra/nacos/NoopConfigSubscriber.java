package edu.whut.eval.infra.nacos;

public class NoopConfigSubscriber implements ConfigSubscriber {

    @Override
    public ConfigSubscription subscribe(ConfigDefinition definition, ConfigChangeHandler handler) {
        return new NoopConfigSubscription(definition);
    }

    private record NoopConfigSubscription(ConfigDefinition definition) implements ConfigSubscription {
        @Override
        public void close() {
        }
    }
}
