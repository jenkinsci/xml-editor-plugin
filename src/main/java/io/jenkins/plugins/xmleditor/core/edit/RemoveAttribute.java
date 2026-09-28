package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import java.util.Optional;

/** Removes an attribute (and the whitespace before it) from the selected elements, when present. */
public final class RemoveAttribute extends XmlOperation {

    private static final long serialVersionUID = 1L;

    private final String name;

    public RemoveAttribute(String xpath, String expected, String name) {
        super(xpath, expected);
        this.name = name;
    }

    @Override
    public String type() {
        return "removeAttribute";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        if (!(target instanceof LElement e)) {
            throw unsupported(target);
        }
        Optional<LAttribute> attribute = e.attribute(name);
        context.recordOldValue(attribute.map(LAttribute::value).orElse(null));
        if (attribute.isEmpty()) {
            return false;
        }
        e.removeAttribute(attribute.get());
        return true;
    }
}
