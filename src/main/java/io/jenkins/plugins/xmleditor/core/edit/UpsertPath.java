package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The simple XPath subset that {@link Upsert} can create: {@code /a/b[@x='1']/c} with an optional final
 * {@code @attribute}.
 */
final class UpsertPath {

    private static final Pattern PREDICATE =
            Pattern.compile("\\[\\s*@([^\\s=\\]]+)\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\")\\s*\\]");

    record Step(String name, Map<String, String> attributes, String source) {}

    private final List<Step> steps;
    private final String attribute;

    private UpsertPath(List<Step> steps, String attribute) {
        this.steps = Collections.unmodifiableList(steps);
        this.attribute = attribute;
    }

    List<Step> steps() {
        return steps;
    }

    /** Name of the final {@code @attribute} step, or {@code null}. */
    String attribute() {
        return attribute;
    }

    /** XPath of the first {@code count} element steps. */
    String prefix(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append('/').append(steps.get(i).source());
        }
        return sb.toString();
    }

    static UpsertPath parse(String xpath) throws XmlEditorException {
        String s = xpath == null ? "" : xpath.strip();
        if (!s.startsWith("/") || s.startsWith("//")) {
            throw unsupported(xpath);
        }
        List<String> segments = split(s.substring(1), xpath);
        List<Step> steps = new ArrayList<>();
        String attribute = null;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (segment.startsWith("@")) {
                if (i != segments.size() - 1 || !isName(segment.substring(1))) {
                    throw unsupported(xpath);
                }
                attribute = segment.substring(1);
            } else {
                steps.add(step(segment, xpath));
            }
        }
        if (steps.isEmpty()) {
            throw unsupported(xpath);
        }
        return new UpsertPath(steps, attribute);
    }

    private static List<String> split(String s, String xpath) throws XmlEditorException {
        List<String> segments = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '/' && depth == 0) {
                segments.add(s.substring(start, i).strip());
                start = i + 1;
            }
        }
        segments.add(s.substring(start).strip());
        if (segments.stream().anyMatch(String::isEmpty)) {
            throw unsupported(xpath);
        }
        return segments;
    }

    private static Step step(String segment, String xpath) throws XmlEditorException {
        int bracket = segment.indexOf('[');
        String name = (bracket < 0 ? segment : segment.substring(0, bracket)).strip();
        if (!isName(name)) {
            throw unsupported(xpath);
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        if (bracket >= 0) {
            Matcher m = PREDICATE.matcher(segment);
            int pos = bracket;
            while (pos < segment.length()) {
                if (!m.find(pos) || m.start() != pos || !isName(m.group(1))) {
                    throw unsupported(xpath);
                }
                attributes.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
                pos = m.end();
            }
        }
        return new Step(name, attributes, segment);
    }

    private static boolean isName(String name) {
        return SetAttribute.XML_NAME.matcher(name).matches();
    }

    private static XmlEditorException unsupported(String xpath) {
        return new XmlEditorException("upsert cannot create '" + xpath + "': to create missing nodes the XPath must be"
                + " an absolute path of element names, such as /Project/PropertyGroup/Version, optionally with"
                + " [@attribute='value'] predicates and a final /@attribute step");
    }
}
