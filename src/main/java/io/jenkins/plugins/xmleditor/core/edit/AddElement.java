package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LParent;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.model.XmlEscaper;
import io.jenkins.plugins.xmleditor.core.parse.LosslessParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Inserts an XML fragment relative to the selected elements. In indented documents the fragment follows the
 * indentation of its new siblings: a compact fragment is laid out like the document, a multi-line fragment keeps its
 * own relative layout. Documents written on a single line stay on a single line.
 */
public final class AddElement extends XmlOperation {

    private static final long serialVersionUID = 1L;

    enum Position {
        LAST_CHILD,
        FIRST_CHILD,
        BEFORE,
        AFTER
    }

    private final String fragment;
    private final String position;

    public AddElement(String xpath, String expected, String fragment, String position) {
        super(xpath, expected);
        this.fragment = fragment == null ? "" : fragment;
        this.position = position;
    }

    @Override
    public String type() {
        return "addElement";
    }

    @Override
    protected boolean applyTo(LItem target, EditContext context) throws XmlEditorException {
        if (!(target instanceof LElement e)) {
            throw unsupported(target);
        }
        Position pos = parsePosition();
        boolean sibling = pos == Position.BEFORE || pos == Position.AFTER;
        if (sibling && e.parent() instanceof LDocument) {
            throw new XmlEditorException("Cannot insert " + pos + " the root element <" + e.name() + ">");
        }
        IndentationStyle style = context.indentation();
        String indent = sibling ? IndentationStyle.indentOf(e) : style.childIndent(e);
        boolean lineMode = indent != null && (sibling || hasLineBreak(e) || isEmpty(e));
        String sep = context.lineSeparator();
        List<LNode> nodes = parseFragment(lineMode ? indent : null, style.unit(), sep);
        List<LNode> sequence = new ArrayList<>();
        for (LNode n : nodes) {
            if (lineMode && !sequence.isEmpty()) {
                sequence.add(new LText(sep + indent));
            }
            sequence.add(n);
        }
        switch (pos) {
            case LAST_CHILD -> appendChildren(e, sequence, lineMode ? sep + indent : null, sep);
            case FIRST_CHILD -> prependChildren(e, sequence, lineMode ? sep + indent : null, sep);
            case BEFORE -> {
                if (lineMode) {
                    sequence.add(new LText(sep + indent));
                }
                insertAll(e.parent(), e.parent().indexOf(e), sequence);
            }
            case AFTER -> {
                if (lineMode) {
                    sequence.add(0, new LText(sep + indent));
                }
                insertAll(e.parent(), e.parent().indexOf(e) + 1, sequence);
            }
        }
        for (LNode n : nodes) {
            checkNamespaces(n);
        }
        return true;
    }

    private Position parsePosition() throws XmlEditorException {
        if (position == null || position.isBlank()) {
            return Position.LAST_CHILD;
        }
        try {
            return Position.valueOf(position.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new XmlEditorException(
                    "Invalid position '" + position + "': use LAST_CHILD, FIRST_CHILD, BEFORE or AFTER");
        }
    }

    private static void appendChildren(LElement e, List<LNode> sequence, String lineStart, String sep) {
        if (e.selfClosing()) {
            e.setSelfClosing(false);
        }
        if (lineStart == null) {
            insertAll(e, e.children().size(), sequence);
            return;
        }
        List<LNode> children = e.children();
        LNode last = children.isEmpty() ? null : children.get(children.size() - 1);
        List<LNode> toInsert = new ArrayList<>();
        toInsert.add(new LText(lineStart));
        toInsert.addAll(sequence);
        if (last instanceof LText t && t.isWhitespace() && t.raw().contains("\n")) {
            insertAll(e, children.size() - 1, toInsert);
        } else {
            if (children.isEmpty()) {
                String parentIndent = Objects.requireNonNullElse(IndentationStyle.indentOf(e), "");
                toInsert.add(new LText(sep + parentIndent));
            }
            insertAll(e, children.size(), toInsert);
        }
    }

    private static void prependChildren(LElement e, List<LNode> sequence, String lineStart, String sep) {
        if (lineStart == null) {
            if (e.selfClosing()) {
                e.setSelfClosing(false);
            }
            insertAll(e, 0, sequence);
            return;
        }
        if (isEmpty(e)) {
            appendChildren(e, sequence, lineStart, sep);
            return;
        }
        List<LNode> toInsert = new ArrayList<>(sequence);
        toInsert.add(new LText(lineStart));
        LNode first = e.children().get(0);
        boolean leadingBreak =
                first instanceof LText t && t.isWhitespace() && t.raw().contains("\n");
        insertAll(e, leadingBreak ? 1 : 0, toInsert);
    }

    private static void insertAll(LParent parent, int index, List<LNode> nodes) {
        int i = index;
        for (LNode n : nodes) {
            parent.insert(i++, n);
        }
    }

    private static boolean hasLineBreak(LElement e) {
        return e.children().stream()
                .anyMatch(
                        c -> c instanceof LText t && t.isWhitespace() && t.raw().contains("\n"));
    }

    private static boolean isEmpty(LElement e) {
        return e.children().isEmpty();
    }

    /** Parses the fragment laid out for {@code indent} (or as written when {@code indent} is null). */
    private List<LNode> parseFragment(String indent, String unit, String sep) throws XmlEditorException {
        String f = XmlEscaper.normalizeLineEndings(fragment).strip();
        if (f.isEmpty()) {
            throw new XmlEditorException("The XML fragment of addElement must not be empty");
        }
        boolean multiLine = f.contains("\n");
        if (indent != null && multiLine) {
            f = reindent(f, indent);
        }
        List<LNode> nodes;
        try {
            nodes = LosslessParser.parseFragment(f.replace("\n", sep));
        } catch (XmlEditorException ex) {
            throw new XmlEditorException("Invalid XML fragment: " + ex.getMessage(), ex);
        }
        if (indent != null && !multiLine) {
            for (LNode n : nodes) {
                if (n instanceof LElement el) {
                    prettyPrint(el, indent, unit, sep);
                }
            }
        }
        return nodes;
    }

    /** Shifts lines 2..n so that their common indentation becomes {@code indent}. */
    private static String reindent(String f, String indent) {
        String[] lines = f.split("\n", -1);
        int common = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                common = Math.min(common, leadingWhitespace(lines[i]));
            }
        }
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            out.append('\n');
            if (!lines[i].isBlank()) {
                out.append(indent).append(lines[i].substring(common));
            }
        }
        return out.toString();
    }

    private static int leadingWhitespace(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return n;
    }

    /** Puts the children of element-only content on their own lines, recursively. */
    private static void prettyPrint(LElement e, String indent, String unit, String sep) {
        List<LNode> children = new ArrayList<>(e.children());
        if (children.isEmpty() || children.stream().anyMatch(c -> c instanceof LText || c instanceof LCData)) {
            return;
        }
        String childIndent = indent + unit;
        for (LNode child : children) {
            e.insert(e.indexOf(child), new LText(sep + childIndent));
            if (child instanceof LElement c) {
                prettyPrint(c, childIndent, unit, sep);
            }
        }
        e.append(new LText(sep + indent));
    }

    private static void checkNamespaces(LNode node) throws XmlEditorException {
        if (!(node instanceof LElement e)) {
            return;
        }
        checkPrefix(e, e.prefix());
        for (LAttribute a : e.attributes()) {
            if (!a.isNamespaceDeclaration()) {
                checkPrefix(e, a.prefix());
            }
        }
        for (LNode child : e.children()) {
            checkNamespaces(child);
        }
    }

    private static void checkPrefix(LElement e, String prefix) throws XmlEditorException {
        if (!prefix.isEmpty() && e.namespaceUri(prefix) == null) {
            throw new XmlEditorException("Namespace prefix '" + prefix + "' used in the fragment is not declared;"
                    + " declare it in the fragment (xmlns:" + prefix + "=\"...\") or in the document");
        }
    }
}
