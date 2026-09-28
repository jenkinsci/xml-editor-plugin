package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.xpath.XPathEngine;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.util.ArrayList;
import java.util.List;

/** Applies a list of operations, in order, to a document. */
public final class XmlEditor {

    private XmlEditor() {}

    /**
     * Applies {@code operations} in order; each one sees the result of the previous ones. On error the document may be
     * partially modified: callers must not write it (all or nothing).
     */
    public static EditResult apply(LDocument doc, List<? extends XmlOperation> operations, XPathOptions options)
            throws XmlEditorException {
        String before = doc.serialize();
        XPathEngine engine = new XPathEngine(doc, options);
        EditContext context = new EditContext(doc);
        List<OperationReport> reports = new ArrayList<>();
        int index = 0;
        for (XmlOperation op : operations) {
            index++;
            String label = "Operation " + index + " (" + op.type() + ")";
            Expectation expectation = Expectation.parse(op.expected() != null ? op.expected() : op.defaultExpected());
            List<LItem> matches = engine.selectNodes(op.xpath());
            expectation.check(matches.size(), label, op.xpath());
            int modified = 0;
            for (LItem target : matches) {
                if (attached(target, doc)) {
                    try {
                        if (op.applyTo(target, context)) {
                            modified++;
                        }
                    } catch (XmlEditorException e) {
                        throw new XmlEditorException(label + ": " + e.getMessage(), e);
                    }
                }
            }
            if (matches.isEmpty()) {
                try {
                    if (op.applyWhenNoMatch(context, engine)) {
                        modified = 1;
                    }
                } catch (XmlEditorException e) {
                    throw new XmlEditorException(label + ": " + e.getMessage(), e);
                }
            }
            if (modified > 0) {
                engine.refresh();
            }
            reports.add(new OperationReport(op.type(), op.xpath(), matches.size(), modified, context.takeOldValues()));
        }
        String after = doc.serialize();
        return new EditResult(!before.equals(after), reports, before, after, engine.warnings());
    }

    /** {@code false} for nodes removed by an earlier match of the same operation (e.g. nested matches). */
    private static boolean attached(LItem item, LDocument doc) {
        LNode node = item instanceof LAttribute a ? a.owner() : (LNode) item;
        if (item instanceof LAttribute a
                && (a.owner() == null || !a.owner().attributes().contains(a))) {
            return false;
        }
        return node != null && node.document() == doc;
    }
}
