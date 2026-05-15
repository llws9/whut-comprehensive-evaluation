package edu.whut.eval.infra.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.infra.nacos.exception.NacosConfigSubscribeException;
import edu.whut.eval.infra.nacos.model.RawConfigPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NacosConfigSubscriber implements ConfigSubscriber, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSubscriber.class);
    private static final String SOURCE = "nacos-listener";

    private final ConfigService configService;
    private final Set<ConfigSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();

    public NacosConfigSubscriber(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public ConfigSubscription subscribe(ConfigDefinition definition, ConfigChangeHandler handler) {
        NacosListenerAdapter listener = new NacosListenerAdapter(definition, handler);
        try {
            configService.addListener(definition.resource().dataId(), definition.resource().group(), listener);
            AppLog.info(log, "nacos.config.listener.registered",
                    "definition", definition.name(),
                    "dataId", definition.resource().dataId(),
                    "group", definition.resource().group());
            ConfigSubscription subscription = new NacosConfigSubscription(definition, listener);
            activeSubscriptions.add(subscription);
            return subscription;
        } catch (NacosException exception) {
            throw new NacosConfigSubscribeException(definition, exception);
        }
    }

    @Override
    public void destroy() {
        activeSubscriptions.forEach(ConfigSubscription::close);
        activeSubscriptions.clear();
    }

    private final class NacosConfigSubscription implements ConfigSubscription {
        private final ConfigDefinition definition;
        private final NacosListenerAdapter listener;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private NacosConfigSubscription(ConfigDefinition definition, NacosListenerAdapter listener) {
            this.definition = definition;
            this.listener = listener;
        }

        @Override
        public ConfigDefinition definition() {
            return definition;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            configService.removeListener(definition.resource().dataId(), definition.resource().group(), listener);
            activeSubscriptions.remove(this);
            AppLog.info(log, "nacos.config.listener.removed",
                    "definition", definition.name(),
                    "dataId", definition.resource().dataId(),
                    "group", definition.resource().group());
        }
    }

    private static final class NacosListenerAdapter extends AbstractListener {
        private final ConfigDefinition definition;
        private final ConfigChangeHandler handler;

        private NacosListenerAdapter(ConfigDefinition definition, ConfigChangeHandler handler) {
            this.definition = definition;
            this.handler = handler;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            RawConfigPayload payload = new RawConfigPayload(definition.resource(), configInfo, SOURCE, Instant.now());
            handler.onChange(definition, payload);
        }
    }
}
