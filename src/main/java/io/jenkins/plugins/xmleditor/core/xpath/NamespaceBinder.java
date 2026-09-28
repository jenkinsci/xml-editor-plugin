package io.jenkins.plugins.xmleditor.core.xpath;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

/**
 * Prefix bindings for XPath: every prefix declared in the document (first declaration wins), overridden by the
 * user's {@code namespaces}. Also decides whether the default namespace is "transparent".
 */
final class NamespaceBinder implements NamespaceContext {

    private static final Pattern STRING_LITERAL = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern PREFIX_USE = Pattern.compile("(?<![\\w.\\-])([A-Za-z_][\\w.\\-]*):(?=[A-Za-z_*])");

    private final Map<String, String> bindings = new LinkedHashMap<>();
    private final Set<String> defaultNamespaces = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();
    private final boolean strict;

    NamespaceBinder(LDocument doc, XPathOptions options) {
        collect(doc.root());
        bindings.putAll(options.namespaces());
        boolean prefixForDefault = options.namespaces().values().stream().anyMatch(defaultNamespaces::contains);
        this.strict = options.strictNamespaces() || prefixForDefault;
    }

    private void collect(LElement e) {
        for (LAttribute a : e.attributes()) {
            if (a.name().equals("xmlns")) {
                if (!a.value().isEmpty()) {
                    defaultNamespaces.add(a.value());
                }
            } else if (a.name().startsWith("xmlns:")) {
                String prefix = a.localName();
                String existing = bindings.putIfAbsent(prefix, a.value());
                if (existing != null && !existing.equals(a.value())) {
                    warnings.add("Namespace prefix '" + prefix + "' is bound to both '" + existing + "' and '"
                            + a.value() + "' in the document; XPath uses '" + existing
                            + "'. Use the namespaces parameter to choose explicitly.");
                }
            }
        }
        for (LNode child : e.children()) {
            if (child instanceof LElement c) {
                collect(c);
            }
        }
    }

    /** {@code true} when elements in the default namespace must be addressed with a prefix. */
    boolean strict() {
        return strict;
    }

    List<String> warnings() {
        return warnings;
    }

    /** Fails with a helpful message when the expression uses a prefix that is not bound. */
    void checkPrefixes(String expression) throws XmlEditorException {
        Matcher m = PREFIX_USE.matcher(STRING_LITERAL.matcher(expression).replaceAll("''"));
        while (m.find()) {
            String prefix = m.group(1);
            if (!prefix.equals(XMLConstants.XML_NS_PREFIX) && !bindings.containsKey(prefix)) {
                throw new XmlEditorException("Unknown namespace prefix '" + prefix + "' in XPath '" + expression
                        + "'. Declare it with namespaces: [" + prefix + ": 'uri']");
            }
        }
    }

    @Override
    public String getNamespaceURI(String prefix) {
        if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        }
        return bindings.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(String namespaceURI) {
        return bindings.entrySet().stream()
                .filter(en -> en.getValue().equals(namespaceURI))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
        return bindings.entrySet().stream()
                .filter(en -> en.getValue().equals(namespaceURI))
                .map(Map.Entry::getKey)
                .iterator();
    }
}
