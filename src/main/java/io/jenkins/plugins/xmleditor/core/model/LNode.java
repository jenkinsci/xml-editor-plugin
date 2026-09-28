package io.jenkins.plugins.xmleditor.core.model;

/** A node of the lossless tree. Serializing a node writes back its exact source text. */
public abstract class LNode implements LItem {

    LParent parent;

    public LParent parent() {
        return parent;
    }

    /** The document this node belongs to, or {@code null} for detached nodes. */
    public LDocument document() {
        LNode n = this;
        while (n != null && !(n instanceof LDocument)) {
            n = n.parent;
        }
        return (LDocument) n;
    }

    public abstract void write(StringBuilder out);

    public String toXml() {
        StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }
}
