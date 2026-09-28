package io.jenkins.plugins.xmleditor.core.model;

/** A {@code <![CDATA[...]]>} section. */
public final class LCData extends LRawNode {

    public LCData(String raw) {
        super(raw);
    }

    public String value() {
        return XmlEscaper.normalizeLineEndings(raw().substring("<![CDATA[".length(), raw().length() - "]]>".length()));
    }

    /** Replaces the content; {@code value} must not contain {@code ]]>}. */
    public void setValue(String value) {
        setRaw("<![CDATA[" + value + "]]>");
    }
}
