package io.jenkins.plugins.xmleditor.core.model;

/** A {@code <?target data?>} processing instruction. */
public final class LProcessingInstruction extends LRawNode {

    public LProcessingInstruction(String raw) {
        super(raw);
    }

    public String target() {
        String body = raw().substring(2, raw().length() - 2);
        int end = 0;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return body.substring(0, end);
    }

    public String data() {
        String body = raw().substring(2, raw().length() - 2);
        return body.substring(target().length()).strip();
    }
}
