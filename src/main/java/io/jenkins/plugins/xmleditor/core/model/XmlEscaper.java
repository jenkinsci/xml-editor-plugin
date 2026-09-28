package io.jenkins.plugins.xmleditor.core.model;

import java.util.Map;
import java.util.function.Predicate;

/** Decoding of raw XML text and escaping of new values. */
public final class XmlEscaper {

    private static final Map<String, String> PREDEFINED =
            Map.of("lt", "<", "gt", ">", "amp", "&", "apos", "'", "quot", "\"");

    private XmlEscaper() {}

    public static String normalizeLineEndings(String s) {
        if (s.indexOf('\r') < 0) {
            return s;
        }
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Decodes predefined, character and internal entity references. Unknown entities stay as literal
     * {@code &name;}. For attributes, literal whitespace characters are normalized to spaces.
     */
    public static String decode(String raw, Map<String, String> entities, boolean attribute) {
        String s = normalizeLineEndings(raw);
        if (s.indexOf('&') < 0 && !(attribute && (s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0))) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                String ref = s.substring(i + 1, semi);
                out.append(resolve(ref, entities));
                i = semi + 1;
            } else {
                out.append(attribute && (c == '\t' || c == '\n') ? ' ' : c);
                i++;
            }
        }
        return out.toString();
    }

    private static String resolve(String ref, Map<String, String> entities) {
        try {
            if (ref.startsWith("#x")) {
                return new String(Character.toChars(Integer.parseInt(ref.substring(2), 16)));
            }
            if (ref.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(ref.substring(1))));
            }
        } catch (IllegalArgumentException e) {
            return "&" + ref + ";";
        }
        String predefined = PREDEFINED.get(ref);
        if (predefined != null) {
            return predefined;
        }
        String internal = entities.get(ref);
        return internal != null ? internal : "&" + ref + ";";
    }

    /** Escapes a value for element content; characters the file encoding cannot hold become references. */
    public static String escapeText(String value, Predicate<CharSequence> canEncode) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        int i = 0;
        while (i < value.length()) {
            int cp = value.codePointAt(i);
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '\r' -> out.append("&#13;");
                default -> appendCodePoint(out, cp, canEncode);
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /** Escapes a value for an attribute delimited by {@code quote}. */
    public static String escapeAttribute(String value, char quote, Predicate<CharSequence> canEncode) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        int i = 0;
        while (i < value.length()) {
            int cp = value.codePointAt(i);
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '\t' -> out.append("&#9;");
                case '\n' -> out.append("&#10;");
                case '\r' -> out.append("&#13;");
                case '"' -> out.append(quote == '"' ? "&quot;" : "\"");
                case '\'' -> out.append(quote == '\'' ? "&apos;" : "'");
                default -> appendCodePoint(out, cp, canEncode);
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static void appendCodePoint(StringBuilder out, int cp, Predicate<CharSequence> canEncode) {
        String ch = new String(Character.toChars(cp));
        if (cp < 0x80 || canEncode.test(ch)) {
            out.append(ch);
        } else {
            out.append("&#").append(cp).append(';');
        }
    }
}
