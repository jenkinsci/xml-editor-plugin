package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LParent;
import io.jenkins.plugins.xmleditor.core.model.LText;
import java.util.List;

/**
 * Removes the selected nodes. A node that sits alone on its line is removed together with its line, so that no blank
 * line is left behind.
 */
public final class Remove extends XmlOperation {

    private static final long serialVersionUID = 1L;

    public Remove(String xpath, String expected) {
        super(xpath, expected);
    }

    @Override
    public String type() {
        return "remove";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        if (!(target instanceof LElement e && e.parent() instanceof LDocument)) {
            context.recordOldValue(EditContext.stringValue(target));
        }
        if (target instanceof LAttribute a) {
            a.owner().removeAttribute(a);
            return true;
        }
        LNode node = (LNode) target;
        LParent parent = node.parent();
        if (node instanceof LElement e && parent instanceof LDocument) {
            throw new XmlEditorException("Cannot remove the root element <" + e.name() + ">");
        }
        List<LNode> children = parent.children();
        int i = parent.indexOf(node);
        LNode prev = i > 0 ? children.get(i - 1) : null;
        LNode next = i + 1 < children.size() ? children.get(i + 1) : null;
        boolean startsLine = prev instanceof LText p && lastLineIsBlank(p.raw());
        boolean endsLine = next instanceof LText n && firstLineIsBlank(n.raw());
        parent.remove(node);
        if (startsLine && endsLine) {
            LText p = (LText) prev;
            String raw = p.raw();
            int nl = raw.lastIndexOf('\n');
            int cut = nl > 0 && raw.charAt(nl - 1) == '\r' ? nl - 1 : nl;
            String rest = raw.substring(0, cut);
            if (rest.isEmpty()) {
                parent.remove(p);
            } else {
                p.setRaw(rest);
            }
        }
        return true;
    }

    private static boolean lastLineIsBlank(String raw) {
        int nl = raw.lastIndexOf('\n');
        return nl >= 0 && IndentationStyle.isBlank(raw.substring(nl + 1));
    }

    private static boolean firstLineIsBlank(String raw) {
        int nl = raw.indexOf('\n');
        return nl >= 0 && IndentationStyle.isBlank(raw.substring(0, nl).replace("\r", ""));
    }
}
