package io.jenkins.plugins.xmleditor.core.model;

import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import java.util.Collections;
import java.util.Map;

/** The whole file: prolog nodes, the root element and whatever follows it. */
public final class LDocument extends LParent {

    private final XmlSource source;
    private final Map<String, String> internalEntities;

    public LDocument(XmlSource source, Map<String, String> internalEntities) {
        this.source = source;
        this.internalEntities = Collections.unmodifiableMap(internalEntities);
    }

    /** Encoding, BOM and line separator of the original file; {@code null} for parsed fragments. */
    public XmlSource source() {
        return source;
    }

    /** General entities declared with a literal value in the internal DTD subset. */
    public Map<String, String> internalEntities() {
        return internalEntities;
    }

    public LElement root() {
        for (LNode child : children()) {
            if (child instanceof LElement e) {
                return e;
            }
        }
        throw new IllegalStateException("document without root element");
    }

    public String serialize() {
        return toXml();
    }

    @Override
    public void write(StringBuilder out) {
        writeChildren(out);
    }
}
