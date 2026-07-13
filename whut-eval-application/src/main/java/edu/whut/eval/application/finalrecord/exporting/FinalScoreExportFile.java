package edu.whut.eval.application.finalrecord.exporting;

import java.util.Arrays;
import java.util.Objects;

public final class FinalScoreExportFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    public FinalScoreExportFile(String filename, String contentType, byte[] content) {
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
