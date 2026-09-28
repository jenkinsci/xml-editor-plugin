package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LText;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets the text of elements, the value of attributes, or replaces single text nodes. Only the selected text changes:
 * comments, surrounding whitespace and quotes are kept.
 */
public final class SetText extends XmlOperation {

    private static final long serialVersionUID = 1L;

    private final String value;

    public SetText(String xpath, String expected, String value) {
        super(xpath, expected);
        this.value = value == null ? "" : value;
    }

    @Override
    public String type() {
        return "setText";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        if (!(target instanceof LElement)) {
            context.recordOldValue(EditContext.stringValue(target));
        }
        if (target instanceof LAttribute a) {
            if (a.value().equals(value)) {
                return false;
            }
            a.setRawValue(context.escapeAttribute(value, a.quote()));
            return true;
        }
        if (target instanceof LText t) {
            if (t.value().equals(value)) {
                return false;
            }
            t.setRaw(context.escapeText(value));
            return true;
        }
        if (target instanceof LCData cd) {
            return setCData(cd, context);
        }
        if (target instanceof LElement e) {
            return setElementText(e, context);
        }
        throw unsupported(target);
    }

    private boolean setCData(LCData cd, EditContext context) {
        if (cd.value().equals(value)) {
            return false;
        }
        if (value.contains("]]>")) {
            LElement parent = (LElement) cd.parent();
            parent.insert(parent.indexOf(cd), new LText(context.escapeText(value)));
            parent.remove(cd);
        } else {
            cd.setValue(value);
        }
        return true;
    }

    private boolean setElementText(LElement e, EditContext context) throws XmlEditorException {
        List<LNode> textNodes = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (LNode child : e.children()) {
            if (child instanceof LElement) {
                throw new XmlEditorException("setText cannot replace the content of element <" + e.name()
                        + "> because it has child elements; use remove and addElement instead");
            } else if (child instanceof LText t) {
                textNodes.add(t);
                current.append(t.value());
            } else if (child instanceof LCData cd) {
                textNodes.add(cd);
                current.append(cd.value());
            }
        }
        context.recordOldValue(current.toString());
        if (current.toString().equals(value)) {
            return false;
        }
        if (textNodes.size() == 1 && textNodes.get(0) instanceof LCData cd) {
            return setCData(cd, context);
        }
        if (e.selfClosing()) {
            e.setSelfClosing(false);
        }
        int position = textNodes.isEmpty() ? e.children().size() : e.indexOf(textNodes.get(0));
        for (LNode n : textNodes) {
            e.remove(n);
        }
        e.insert(position, new LText(context.escapeText(value)));
        return true;
    }
}
