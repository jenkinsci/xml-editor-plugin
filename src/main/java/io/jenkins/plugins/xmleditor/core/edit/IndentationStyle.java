package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LParent;
import io.jenkins.plugins.xmleditor.core.model.LText;
import java.util.HashMap;
import java.util.Map;

/** Indentation conventions of a document, used only to lay out new content. */
public final class IndentationStyle {

    static final String DEFAULT_UNIT = "    ";

    private final String unit;

    private IndentationStyle(String unit) {
        this.unit = unit;
    }

    /** Detects the indentation unit as the most frequent difference between a parent's and a child's indent. */
    public static IndentationStyle detect(LDocument doc) {
        Map<String, Integer> counts = new HashMap<>();
        count(doc.root(), counts);
        String unit = counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(e -> -e.getKey().length()))
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_UNIT);
        return new IndentationStyle(unit);
    }

    private static void count(LElement e, Map<String, Integer> counts) {
        String parentIndent = indentOf(e);
        for (LNode child : e.children()) {
            if (child instanceof LElement c) {
                String childIndent = indentOf(c);
                if (parentIndent != null
                        && childIndent != null
                        && childIndent.length() > parentIndent.length()
                        && childIndent.startsWith(parentIndent)) {
                    counts.merge(childIndent.substring(parentIndent.length()), 1, Integer::sum);
                }
                count(c, counts);
            }
        }
    }

    public String unit() {
        return unit;
    }

    /**
     * The whitespace before {@code node} on its line when the node starts a line, otherwise {@code null}. The root
     * element and top-level nodes at the start of the file have an empty indentation.
     */
    public static String indentOf(LNode node) {
        LParent parent = node.parent();
        if (parent == null) {
            return null;
        }
        int i = parent.indexOf(node);
        if (i == 0) {
            return parent instanceof LDocument ? "" : null;
        }
        if (parent.children().get(i - 1) instanceof LText t) {
            String raw = t.raw();
            int nl = raw.lastIndexOf('\n');
            if (nl >= 0) {
                String after = raw.substring(nl + 1);
                return isBlank(after) ? after : null;
            }
        }
        return null;
    }

    /** Indentation for children of {@code parent}: the one of its existing children, or the parent's plus a unit. */
    public String childIndent(LElement parent) {
        for (LNode child : parent.children()) {
            if (child instanceof LElement) {
                String indent = indentOf(child);
                if (indent != null) {
                    return indent;
                }
            }
        }
        String parentIndent = indentOf(parent);
        return parentIndent == null ? null : parentIndent + unit;
    }

    static boolean isBlank(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }
}
