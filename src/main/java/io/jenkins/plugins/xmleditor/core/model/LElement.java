package io.jenkins.plugins.xmleditor.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;

/**
 * An element, stored as the pieces of its source text: name, attributes (each with its own spacing and quotes),
 * the whitespace before {@code >} or {@code />}, children and the exact end tag.
 */
public final class LElement extends LParent {

    private final String name;
    private final List<LAttribute> attributes = new ArrayList<>();
    private String startTagTail;
    private boolean selfClosing;
    private String endTag;

    public LElement(String name, String startTagTail, boolean selfClosing, String endTag) {
        this.name = name;
        this.startTagTail = startTagTail;
        this.selfClosing = selfClosing;
        this.endTag = endTag;
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

    public List<LAttribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public Optional<LAttribute> attribute(String qname) {
        return attributes.stream().filter(a -> a.name().equals(qname)).findFirst();
    }

    public void addAttribute(LAttribute attribute) {
        attribute.owner = this;
        attributes.add(attribute);
    }

    public void removeAttribute(LAttribute attribute) {
        attributes.remove(attribute);
        attribute.owner = null;
    }

    /** Whitespace between the last attribute (or the name) and {@code >} / {@code />}. */
    public String startTagTail() {
        return startTagTail;
    }

    public boolean selfClosing() {
        return selfClosing;
    }

    /**
     * Switches between {@code <a/>} and {@code <a></a>}. Opening a self-closing element drops whitespace before
     * {@code />} unless it contains a line break (multi-line start tags keep their layout).
     */
    public void setSelfClosing(boolean selfClosing) {
        if (this.selfClosing == selfClosing) {
            return;
        }
        this.selfClosing = selfClosing;
        if (selfClosing) {
            endTag = null;
        } else {
            if (startTagTail.indexOf('\n') < 0) {
                startTagTail = "";
            }
            endTag = "</" + name + ">";
        }
    }

    /** The exact end tag (e.g. {@code </a >}), or {@code null} for a self-closing element. */
    public String endTag() {
        return endTag;
    }

    public void setEndTag(String endTag) {
        this.endTag = endTag;
    }

    /**
     * Resolves a namespace prefix ({@code ""} for the default namespace) using the declarations in scope.
     *
     * @return the URI, {@code ""} for "no namespace", or {@code null} when the prefix is not declared
     */
    public String namespaceUri(String prefix) {
        if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        }
        String declaration = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        for (LNode n = this; n instanceof LElement; n = n.parent) {
            Optional<LAttribute> decl = ((LElement) n).attribute(declaration);
            if (decl.isPresent()) {
                return decl.get().value();
            }
        }
        return prefix.isEmpty() ? "" : null;
    }

    @Override
    public void write(StringBuilder out) {
        out.append('<').append(name);
        for (LAttribute a : attributes) {
            a.write(out);
        }
        out.append(startTagTail);
        if (selfClosing) {
            out.append("/>");
        } else {
            out.append('>');
            writeChildren(out);
            out.append(endTag);
        }
    }
}
