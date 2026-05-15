package edu.whut.eval.infra.nacos.exception;

import edu.whut.eval.common.exception.ConfigLoadException;

public class NacosBootstrapException extends ConfigLoadException {

    public NacosBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
