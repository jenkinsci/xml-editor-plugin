package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sets an attribute on the selected elements. An existing attribute keeps its quotes and position; a new one is
 * appended following the layout of the existing attributes (same line or one per line).
 */
public final class SetAttribute extends XmlOperation {

    private static final long serialVersionUID = 1L;

    static final Pattern XML_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_.\\-]*(:[\\p{L}_][\\p{L}\\p{N}_.\\-]*)?");

    private final String name;
    private final String value;

    public SetAttribute(String xpath, String expected, String name, String value) {
        super(xpath, expected);
        this.name = name;
        this.value = value == null ? "" : value;
    }

    @Override
    public String type() {
        return "setAttribute";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        if (!(target instanceof LElement e)) {
            throw unsupported(target);
        }
        checkName(name, e);
        Optional<LAttribute> existing = e.attribute(name);
        context.recordOldValue(existing.map(LAttribute::value).orElse(null));
        if (existing.isPresent()) {
            LAttribute a = existing.get();
            if (a.value().equals(value)) {
                return false;
            }
            a.setRawValue(context.escapeAttribute(value, a.quote()));
            return true;
        }
        List<LAttribute> attributes = e.attributes();
        LAttribute last = attributes.isEmpty() ? null : attributes.get(attributes.size() - 1);
        String leading = last != null && last.leadingWhitespace().contains("\n") ? last.leadingWhitespace() : " ";
        char quote = last != null ? last.quote() : context.prevailingQuote();
        e.addAttribute(new LAttribute(leading, name, "=", quote, context.escapeAttribute(value, quote)));
        return true;
    }

    static void checkName(String name, LElement e) throws XmlEditorException {
        if (name == null || !XML_NAME.matcher(name).matches() || name.equals("xmlns") || name.startsWith("xmlns:")) {
            throw new XmlEditorException("Invalid attribute name '" + name + "'");
        }
        int colon = name.indexOf(':');
        if (colon > 0 && e.namespaceUri(name.substring(0, colon)) == null) {
            throw new XmlEditorException("Namespace prefix '" + name.substring(0, colon) + "' of attribute '" + name
                    + "' is not declared on <" + e.name() + "> or its ancestors");
        }
    }
}
