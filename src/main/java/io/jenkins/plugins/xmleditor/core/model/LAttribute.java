package io.jenkins.plugins.xmleditor.core.model;

import java.util.Map;

/** An attribute with the exact whitespace before it, the text around {@code =}, its quote and its raw value. */
public final class LAttribute implements LItem {

    LElement owner;
    private final String leadingWhitespace;
    private final String name;
    private final String equalsRaw;
    private final char quote;
    private String rawValue;

    public LAttribute(String leadingWhitespace, String name, String equalsRaw, char quote, String rawValue) {
        this.leadingWhitespace = leadingWhitespace;
        this.name = name;
        this.equalsRaw = equalsRaw;
        this.quote = quote;
        this.rawValue = rawValue;
    }

    public LElement owner() {
        return owner;
    }

    public String leadingWhitespace() {
        return leadingWhitespace;
    }

    public String name() {
        return name;
    }

    public String prefix() {
        int colon = name.indexOf(':');
        return colon < 0 ? "" : name.substring(0, colon);
    }

    public String localName() {
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    public boolean isNamespaceDeclaration() {
        return name.equals("xmlns") || name.startsWith("xmlns:");
    }

    /** The text between the name and the opening quote, e.g. {@code "="} or {@code " = "}. */
    public String equalsRaw() {
        return equalsRaw;
    }

    public char quote() {
        return quote;
    }

    public String rawValue() {
        return rawValue;
    }

    /** Replaces the value; {@code rawValue} must already be escaped for {@link #quote()}. */
    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    /** The normalized value as an XML processor sees it. */
    public String value() {
        LDocument doc = owner == null ? null : owner.document();
        return XmlEscaper.decode(rawValue, doc == null ? Map.of() : doc.internalEntities(), true);
    }

    void write(StringBuilder out) {
        out.append(leadingWhitespace)
                .append(name)
                .append(equalsRaw)
                .append(quote)
                .append(rawValue)
                .append(quote);
    }
}
