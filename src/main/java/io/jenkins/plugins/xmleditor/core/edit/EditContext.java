package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LComment;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LProcessingInstruction;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.model.XmlEscaper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** What operations need to know about the document they modify: encoding, line separator, style. */
public final class EditContext {

    private final LDocument doc;
    private final String lineSeparator;
    private final Predicate<CharSequence> canEncode;
    private IndentationStyle indentation;
    private final List<String> oldValues = new ArrayList<>();

    public EditContext(LDocument doc) {
        this.doc = doc;
        XmlSource source = doc.source();
        this.lineSeparator = source == null ? "\n" : source.lineSeparator();
        this.canEncode = source == null ? s -> true : source::canEncode;
    }

    /** Records the value a node had before the current operation touched it. */
    public void recordOldValue(String value) {
        oldValues.add(value);
    }

    /** Returns and clears the values recorded since the last call. */
    List<String> takeOldValues() {
        List<String> values = new ArrayList<>(oldValues);
        oldValues.clear();
        return values;
    }

    /** XPath string-value: text of an element and its descendants, value of attributes, text and comments. */
    public static String stringValue(LItem item) {
        if (item instanceof LAttribute a) {
            return a.value();
        } else if (item instanceof LText t) {
            return t.value();
        } else if (item instanceof LCData cd) {
            return cd.value();
        } else if (item instanceof LComment c) {
            return c.text();
        } else if (item instanceof LProcessingInstruction pi) {
            return pi.data();
        } else if (item instanceof LElement e) {
            StringBuilder sb = new StringBuilder();
            appendText(e, sb);
            return sb.toString();
        }
        return "";
    }

    private static void appendText(LElement e, StringBuilder sb) {
        for (LNode child : e.children()) {
            if (child instanceof LElement c) {
                appendText(c, sb);
            } else if (child instanceof LText || child instanceof LCData) {
                sb.append(stringValue(child));
            }
        }
    }

    public LDocument doc() {
        return doc;
    }

    public String lineSeparator() {
        return lineSeparator;
    }

    /** Indentation conventions, detected once from the document as it was before the first insertion. */
    public IndentationStyle indentation() {
        if (indentation == null) {
            indentation = IndentationStyle.detect(doc);
        }
        return indentation;
    }

    /** Escapes element content; line breaks follow the file's line separator. */
    public String escapeText(String value) {
        String normalized = XmlEscaper.normalizeLineEndings(value);
        return XmlEscaper.escapeText(normalized, canEncode).replace("\n", lineSeparator);
    }

    public String escapeAttribute(String value, char quote) {
        return XmlEscaper.escapeAttribute(value, quote, canEncode);
    }

    /** The quote used by most attributes of the document ({@code "} when there are none or on a tie). */
    public char dominantQuote() {
        int[] counts = new int[2];
        countQuotes(doc.root(), counts);
        return counts[1] > counts[0] ? '\'' : '"';
    }

    private static void countQuotes(LElement e, int[] counts) {
        for (LAttribute a : e.attributes()) {
            counts[a.quote() == '\'' ? 1 : 0]++;
        }
        for (LNode child : e.children()) {
            if (child instanceof LElement c) {
                countQuotes(c, counts);
            }
        }
    }

    static String describe(LItem item) {
        if (item instanceof LElement e) {
            return "element <" + e.name() + ">";
        } else if (item instanceof LAttribute a) {
            return "attribute @" + a.name();
        } else if (item instanceof LText || item instanceof LCData) {
            return "a text node";
        } else if (item instanceof LComment) {
            return "a comment";
        } else if (item instanceof LProcessingInstruction) {
            return "a processing instruction";
        }
        return "a " + item.getClass().getSimpleName();
    }
}
