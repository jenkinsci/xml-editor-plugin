package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.xpath.XPathEngine;
import java.util.List;
import java.util.Map;

/**
 * Updates the selected nodes like {@link SetText}; when nothing matches, creates the missing part of the path (see
 * {@link UpsertPath}) under the deepest existing element and sets the value there.
 */
public final class Upsert extends XmlOperation {

    private static final long serialVersionUID = 1L;

    private final String value;

    public Upsert(String xpath, String expected, String value) {
        super(xpath, expected);
        this.value = value == null ? "" : value;
    }

    @Override
    public String type() {
        return "upsert";
    }

    @Override
    protected String defaultExpected() {
        return "ANY";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        return new SetText(xpath(), null, value).applyTo(target, context);
    }

    @Override
    protected boolean applyWhenNoMatch(EditContext context, XPathEngine engine) throws XmlEditorException {
        UpsertPath path = UpsertPath.parse(xpath());
        int n = path.steps().size();
        LElement base = null;
        int existing = n;
        for (; existing >= 1; existing--) {
            List<LElement> elements = engine.selectNodes(path.prefix(existing)).stream()
                    .filter(LElement.class::isInstance)
                    .map(LElement.class::cast)
                    .toList();
            if (elements.size() > 1) {
                throw new XmlEditorException("upsert cannot create '" + xpath() + "': '" + path.prefix(existing)
                        + "' matches " + elements.size() + " elements, make the path unique");
            }
            if (elements.size() == 1) {
                base = elements.get(0);
                break;
            }
        }
        if (base == null) {
            throw new XmlEditorException("upsert cannot create '" + xpath() + "': the root element '" + path.prefix(1)
                    + "' does not exist (the root element is never created)");
        }
        if (existing == n) {
            if (path.attribute() == null) {
                return new SetText(xpath(), null, value).applyTo(base, context);
            }
            boolean changed = new SetAttribute(xpath(), null, path.attribute(), value).applyTo(base, context);
            context.takeOldValues(); // nothing existed before: no old value to report
            return changed;
        }
        String fragment = fragment(path, existing, context);
        return new AddElement(xpath(), null, fragment, null).applyTo(base, context);
    }

    /** Compact XML for the steps from {@code from} on; {@link AddElement} lays it out like the document. */
    private String fragment(UpsertPath path, int from, EditContext context) {
        List<UpsertPath.Step> steps = path.steps();
        String inner = null;
        for (int i = steps.size() - 1; i >= from; i--) {
            UpsertPath.Step step = steps.get(i);
            boolean last = i == steps.size() - 1;
            StringBuilder open = new StringBuilder("<").append(step.name());
            for (Map.Entry<String, String> a : step.attributes().entrySet()) {
                appendAttribute(open, a.getKey(), a.getValue(), context);
            }
            if (last && path.attribute() != null) {
                appendAttribute(open, path.attribute(), value, context);
            }
            // line breaks become references: the fragment must stay on one line, or AddElement would re-indent the
            // value
            String text = context.escapeText(value).replace(context.lineSeparator(), "&#10;");
            String content = last ? (path.attribute() == null ? text : "") : inner;
            inner = content.isEmpty() ? open + "/>" : open + ">" + content + "</" + step.name() + ">";
        }
        return inner;
    }

    private static void appendAttribute(StringBuilder sb, String name, String value, EditContext context) {
        sb.append(' ')
                .append(name)
                .append("=\"")
                .append(context.escapeAttribute(value, '"'))
                .append('"');
    }
}
