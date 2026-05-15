package edu.whut.eval.infra.nacos.config;

import edu.whut.eval.infra.nacos.model.ConfigFormat;

import java.util.ArrayList;
import java.util.List;

public class NacosDefinitionProperties {

    private List<Item> definitions = new ArrayList<>();

    public List<Item> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Item> definitions) {
        this.definitions = definitions;
    }

    public static class Item {
        private String name;
        private String dataId;
        private String group;
        private Long timeoutMs;
        private ConfigFormat format = ConfigFormat.YAML;
        private boolean required = true;
        private boolean autoRefresh = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public Long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public ConfigFormat getFormat() {
            return format;
        }

        public void setFormat(ConfigFormat format) {
            this.format = format;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public boolean isAutoRefresh() {
            return autoRefresh;
        }

        public void setAutoRefresh(boolean autoRefresh) {
            this.autoRefresh = autoRefresh;
        }
    }
}
