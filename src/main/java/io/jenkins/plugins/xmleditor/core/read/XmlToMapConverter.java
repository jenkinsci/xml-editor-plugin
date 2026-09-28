package io.jenkins.plugins.xmleditor.core.read;

import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LText;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts an element into plain Pipeline-friendly values:
 *
 * <ul>
 *   <li>an element with only text and no attributes becomes its trimmed text;
 *   <li>otherwise a {@link LinkedHashMap}: attributes as {@code @name}, child elements by their qualified name
 *       (repeated names become an {@link ArrayList}), remaining text as {@code #text};
 *   <li>comments, processing instructions and namespace declarations are ignored.
 * </ul>
 */
public final class XmlToMapConverter {

    private XmlToMapConverter() {}

    public static Serializable convert(LElement element) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (LAttribute a : element.attributes()) {
            if (!a.isNamespaceDeclaration()) {
                map.put("@" + a.name(), a.value());
            }
        }
        StringBuilder text = new StringBuilder();
        for (LNode child : element.children()) {
            if (child instanceof LElement e) {
                add(map, e.name(), convert(e));
            } else if (child instanceof LText t) {
                text.append(t.value());
            } else if (child instanceof LCData cd) {
                text.append(cd.value());
            }
        }
        String trimmed = text.toString().strip();
        if (map.isEmpty()) {
            return trimmed;
        }
        if (!trimmed.isEmpty()) {
            map.put("#text", trimmed);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> map, String key, Object value) {
        Object existing = map.get(key);
        if (existing == null) {
            map.put(key, value);
        } else if (existing instanceof ArrayList) {
            ((List<Object>) existing).add(value);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(existing);
            list.add(value);
            map.put(key, list);
        }
    }
}
