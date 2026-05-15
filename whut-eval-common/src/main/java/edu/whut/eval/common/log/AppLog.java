package edu.whut.eval.common.log;

import org.slf4j.Logger;

import java.util.Objects;

/**
 * Unified project logging helper.
 * Format example:
 * event=iam.user.query userNo=2024305999 source=db
 */
public final class AppLog {

    private AppLog() {
    }

    public static void info(Logger logger, String event, Object... keyValues) {
        if (logger.isInfoEnabled()) {
            logger.info(buildMessage(event, keyValues));
        }
    }

    public static void warn(Logger logger, String event, Object... keyValues) {
        if (logger.isWarnEnabled()) {
            logger.warn(buildMessage(event, keyValues));
        }
    }

    public static void debug(Logger logger, String event, Object... keyValues) {
        if (logger.isDebugEnabled()) {
            logger.debug(buildMessage(event, keyValues));
        }
    }

    public static void error(Logger logger, String event, Object... keyValues) {
        logger.error(buildMessage(event, keyValues));
    }

    public static void error(Logger logger, Throwable throwable, String event, Object... keyValues) {
        logger.error(buildMessage(event, keyValues), throwable);
    }

    private static String buildMessage(String event, Object... keyValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("event=").append(safeValue(event));

        if (keyValues == null || keyValues.length == 0) {
            return builder.toString();
        }

        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = i + 1 < keyValues.length ? keyValues[i + 1] : "<missing>";
            builder.append(' ')
                    .append(safeKey(key))
                    .append('=')
                    .append(safeValue(value));
        }
        return builder.toString();
    }

    private static String safeKey(Object key) {
        String keyText = Objects.toString(key, "unknown");
        return keyText.isBlank() ? "unknown" : keyText;
    }

    private static String safeValue(Object value) {
        return Objects.toString(value, "null")
                .replace('\n', '_')
                .replace('\r', '_')
                .replace('\t', '_');
    }
}
