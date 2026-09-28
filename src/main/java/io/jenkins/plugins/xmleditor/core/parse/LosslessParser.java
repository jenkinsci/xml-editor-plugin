package io.jenkins.plugins.xmleditor.core.parse;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LComment;
import io.jenkins.plugins.xmleditor.core.model.LDoctype;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LParent;
import io.jenkins.plugins.xmleditor.core.model.LProcessingInstruction;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.model.LXmlDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer that builds the lossless tree: every character of the input ends up in exactly one node, so that
 * serializing the tree gives back the input unchanged. Strict well-formedness (names, namespaces, character rules)
 * is checked separately by {@link WellFormednessChecker}; this class only rejects input it cannot structure.
 */
public final class LosslessParser {

    static final int MAX_DEPTH = 1000;

    private static final Pattern ENTITY_DECL =
            Pattern.compile("<!ENTITY\\s+([^\\s%][^\\s]*)\\s+(?:\"([^\"]*)\"|'([^']*)')\\s*>");
    private static final Pattern SUBSET_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private final String s;
    private int pos;
    private final Map<String, String> entities = new HashMap<>();

    private LosslessParser(String s) {
        this.s = s;
    }

    public static LDocument parse(XmlSource source) throws XmlEditorException {
        LosslessParser p = new LosslessParser(source.text());
        LDocument doc = new LDocument(source, p.entities);
        p.parseProlog(doc);
        return doc;
    }

    /** Parses a well-balanced fragment (any number of nodes); the returned nodes are detached. */
    public static List<LNode> parseFragment(String xml) throws XmlEditorException {
        LosslessParser p = new LosslessParser(xml);
        LDocument container = new LDocument(null, Map.of());
        p.parseContent(container, 0);
        if (p.pos < xml.length()) {
            throw p.error("unexpected end tag");
        }
        List<LNode> nodes = new ArrayList<>(container.children());
        for (LNode n : nodes) {
            container.remove(n);
        }
        return nodes;
    }

    private void parseProlog(LDocument doc) throws XmlEditorException {
        if (s.startsWith("<?xml") && s.length() > 5 && isWhitespace(s.charAt(5))) {
            doc.append(new LXmlDeclaration(scanTo("?>")));
        }
        boolean rootSeen = false;
        while (pos < s.length()) {
            if (s.startsWith("<!--", pos)) {
                doc.append(new LComment(scanTo("-->")));
            } else if (s.startsWith("<?", pos)) {
                doc.append(new LProcessingInstruction(scanTo("?>")));
            } else if (s.startsWith("<!DOCTYPE", pos)) {
                if (rootSeen) {
                    throw error("DOCTYPE after the root element");
                }
                doc.append(scanDoctype());
            } else if (s.startsWith("<", pos) && !s.startsWith("</", pos)) {
                if (rootSeen) {
                    throw error("more than one root element");
                }
                doc.append(parseElement(1));
                rootSeen = true;
            } else if (isWhitespace(s.charAt(pos))) {
                int start = pos;
                while (pos < s.length() && isWhitespace(s.charAt(pos))) {
                    pos++;
                }
                doc.append(new LText(s.substring(start, pos)));
            } else {
                throw error("content outside the root element");
            }
        }
        if (!rootSeen) {
            throw error("no root element");
        }
    }

    private void parseContent(LParent parent, int depth) throws XmlEditorException {
        while (pos < s.length()) {
            if (s.startsWith("</", pos)) {
                return;
            } else if (s.startsWith("<!--", pos)) {
                parent.append(new LComment(scanTo("-->")));
            } else if (s.startsWith("<![CDATA[", pos)) {
                parent.append(new LCData(scanTo("]]>")));
            } else if (s.startsWith("<?", pos)) {
                parent.append(new LProcessingInstruction(scanTo("?>")));
            } else if (s.startsWith("<!", pos)) {
                throw error("unexpected markup declaration");
            } else if (s.charAt(pos) == '<') {
                parent.append(parseElement(depth + 1));
            } else {
                int end = s.indexOf('<', pos);
                end = end < 0 ? s.length() : end;
                parent.append(new LText(s.substring(pos, end)));
                pos = end;
            }
        }
    }

    private LElement parseElement(int depth) throws XmlEditorException {
        if (depth > MAX_DEPTH) {
            throw error("elements nested deeper than " + MAX_DEPTH + " levels");
        }
        int tagStart = pos;
        pos++; // '<'
        String name = readName();
        List<LAttribute> attributes = new ArrayList<>();
        while (true) {
            String ws = readWhitespace();
            if (pos >= s.length()) {
                pos = tagStart;
                throw error("unterminated start tag <" + name);
            }
            if (s.startsWith("/>", pos)) {
                pos += 2;
                return element(name, attributes, ws, true, null);
            }
            if (s.charAt(pos) == '>') {
                pos++;
                LElement e = element(name, attributes, ws, false, "</" + name + ">");
                parseContent(e, depth);
                if (!s.startsWith("</", pos)) {
                    pos = tagStart;
                    throw error("element <" + name + "> is not closed");
                }
                int endStart = pos;
                pos += 2;
                String endName = readName();
                if (!endName.equals(name)) {
                    pos = endStart;
                    throw error("end tag </" + endName + "> does not match <" + name + ">");
                }
                readWhitespace();
                expect('>');
                e.setEndTag(s.substring(endStart, pos));
                return e;
            }
            if (ws.isEmpty()) {
                throw error("whitespace expected before attribute");
            }
            attributes.add(parseAttribute(ws));
        }
    }

    private static LElement element(
            String name, List<LAttribute> attributes, String tail, boolean selfClosing, String endTag) {
        LElement e = new LElement(name, tail, selfClosing, endTag);
        attributes.forEach(e::addAttribute);
        return e;
    }

    private LAttribute parseAttribute(String leadingWhitespace) throws XmlEditorException {
        String name = readName();
        int eqStart = pos;
        readWhitespace();
        expect('=');
        readWhitespace();
        String equalsRaw = s.substring(eqStart, pos);
        if (pos >= s.length() || (s.charAt(pos) != '"' && s.charAt(pos) != '\'')) {
            throw error("attribute value must be quoted");
        }
        char quote = s.charAt(pos++);
        int end = s.indexOf(quote, pos);
        if (end < 0) {
            throw error("unterminated attribute value");
        }
        String raw = s.substring(pos, end);
        if (raw.indexOf('<') >= 0) {
            throw error("'<' is not allowed in attribute values");
        }
        pos = end + 1;
        return new LAttribute(leadingWhitespace, name, equalsRaw, quote, raw);
    }

    private LDoctype scanDoctype() throws XmlEditorException {
        int start = pos;
        pos += "<!DOCTYPE".length();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '"' || c == '\'') {
                skipQuoted(c);
            } else if (c == '[') {
                int subsetStart = ++pos;
                scanInternalSubset();
                collectEntities(s.substring(subsetStart, pos));
                pos++; // ']'
            } else if (c == '>') {
                pos++;
                return new LDoctype(s.substring(start, pos));
            } else {
                pos++;
            }
        }
        pos = start;
        throw error("unterminated DOCTYPE");
    }

    private void scanInternalSubset() throws XmlEditorException {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (s.startsWith("<!--", pos)) {
                scanTo("-->");
            } else if (s.startsWith("<?", pos)) {
                scanTo("?>");
            } else if (c == '"' || c == '\'') {
                skipQuoted(c);
            } else if (c == ']') {
                return;
            } else {
                pos++;
            }
        }
        throw error("unterminated internal DTD subset");
    }

    private void collectEntities(String subset) {
        Matcher m = ENTITY_DECL.matcher(SUBSET_COMMENT.matcher(subset).replaceAll(""));
        while (m.find()) {
            entities.putIfAbsent(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
    }

    private void skipQuoted(char quote) throws XmlEditorException {
        int end = s.indexOf(quote, pos + 1);
        if (end < 0) {
            throw error("unterminated quoted string");
        }
        pos = end + 1;
    }

    /** Returns the text from the current position up to and including {@code terminator}. */
    private String scanTo(String terminator) throws XmlEditorException {
        int end = s.indexOf(terminator, pos + 2);
        if (end < 0) {
            throw error("missing '" + terminator + "'");
        }
        String raw = s.substring(pos, end + terminator.length());
        pos = end + terminator.length();
        return raw;
    }

    private String readName() throws XmlEditorException {
        int start = pos;
        while (pos < s.length() && !isNameTerminator(s.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            throw error("name expected");
        }
        return s.substring(start, pos);
    }

    private String readWhitespace() {
        int start = pos;
        while (pos < s.length() && isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return s.substring(start, pos);
    }

    private void expect(char c) throws XmlEditorException {
        if (pos >= s.length() || s.charAt(pos) != c) {
            throw error("'" + c + "' expected");
        }
        pos++;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isNameTerminator(char c) {
        return isWhitespace(c) || c == '/' || c == '>' || c == '=' || c == '<' || c == '"' || c == '\'';
    }

    private XmlEditorException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, s.length()); i++) {
            if (s.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new XmlEditorException("XML syntax error at line " + line + ", column " + column + ": " + message);
    }
}
