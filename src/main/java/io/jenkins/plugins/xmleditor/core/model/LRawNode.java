package io.jenkins.plugins.xmleditor.core.model;

/** A leaf node kept as its exact source text. */
public abstract class LRawNode extends LNode {

    private String raw;

    protected LRawNode(String raw) {
        this.raw = raw;
    }

    public String raw() {
        return raw;
    }

    protected void setRaw(String raw) {
        this.raw = raw;
    }

    @Override
    public void write(StringBuilder out) {
        out.append(raw);
    }
}
