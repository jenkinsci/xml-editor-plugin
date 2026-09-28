package io.jenkins.plugins.xmleditor.core.model;

/** Character data, kept with entity and character references exactly as written. */
public final class LText extends LRawNode {

    public LText(String raw) {
        super(raw);
    }

    /** The text as an XML processor sees it: references decoded, line endings normalized. */
    public String value() {
        LDocument doc = document();
        return XmlEscaper.decode(raw(), doc == null ? java.util.Map.of() : doc.internalEntities(), false);
    }

    public boolean isWhitespace() {
        return raw().chars().allMatch(c -> c == ' ' || c == '\t' || c == '\n' || c == '\r');
    }

    public void setRaw(String raw) {
        super.setRaw(raw);
    }
}
