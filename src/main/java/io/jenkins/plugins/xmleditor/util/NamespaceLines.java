package io.jenkins.plugins.xmleditor.util;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses namespace declarations written one per line as {@code prefix=uri} (used by the Freestyle form). */
public final class NamespaceLines {

    private NamespaceLines() {}

    public static Map<String, String> parse(String text) throws XmlEditorException {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            String prefix = eq < 0 ? "" : line.substring(0, eq).strip();
            String uri = eq < 0 ? "" : line.substring(eq + 1).strip();
            if (prefix.isEmpty() || uri.isEmpty()) {
                throw new XmlEditorException(
                        "Invalid namespace declaration on line " + (i + 1) + ": '" + line + "' (expected prefix=uri)");
            }
            result.put(prefix, uri);
        }
        return result;
    }
}
