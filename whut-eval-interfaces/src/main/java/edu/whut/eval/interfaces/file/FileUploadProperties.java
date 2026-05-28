package edu.whut.eval.interfaces.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "infra.file-upload")
public class FileUploadProperties {

    private long maxFileSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedContentTypes = defaultAllowedContentTypes();

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes == null
                ? defaultAllowedContentTypes()
                : new ArrayList<>(allowedContentTypes);
    }

    public Set<String> normalizedAllowedContentTypes() {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : allowedContentTypes) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static List<String> defaultAllowedContentTypes() {
        List<String> defaults = new ArrayList<>();
        defaults.add("image/png");
        defaults.add("image/jpeg");
        defaults.add("image/gif");
        defaults.add("image/webp");
        defaults.add("application/pdf");
        defaults.add("text/plain");
        return defaults;
    }
}
