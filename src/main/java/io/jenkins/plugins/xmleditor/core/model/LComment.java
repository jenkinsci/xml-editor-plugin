package io.jenkins.plugins.xmleditor.core.model;

/** A {@code <!-- ... -->} comment. */
public final class LComment extends LRawNode {

    public LComment(String raw) {
        super(raw);
    }

    public String text() {
        return raw().substring("<!--".length(), raw().length() - "-->".length());
    }
}
