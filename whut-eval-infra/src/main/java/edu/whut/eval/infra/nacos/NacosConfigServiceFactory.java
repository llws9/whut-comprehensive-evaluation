package edu.whut.eval.infra.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

public final class NacosConfigServiceFactory {

    private NacosConfigServiceFactory() {
    }

    public static ConfigService create(NacosConnectionOptions options) throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, options.serverAddress());

        if (options.namespace() != null && !options.namespace().isBlank()) {
            properties.put(PropertyKeyConst.NAMESPACE, options.namespace());
        }
        if (options.username() != null && !options.username().isBlank()) {
            properties.put(PropertyKeyConst.USERNAME, options.username());
        }
        if (options.password() != null && !options.password().isBlank()) {
            properties.put(PropertyKeyConst.PASSWORD, options.password());
        }
        return NacosFactory.createConfigService(properties);
    }
}
