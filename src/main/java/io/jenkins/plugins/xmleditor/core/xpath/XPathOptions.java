package io.jenkins.plugins.xmleditor.core.xpath;

import java.io.Serializable;
import java.util.Map;

/**
 * How XPath expressions are evaluated.
 *
 * @param namespaces extra prefix → URI bindings (they override the prefixes declared in the document)
 * @param strictNamespaces {@code true} to apply standard XPath 1.0 rules: elements in the default namespace can
 *     only be selected with a prefix bound in {@code namespaces}
 */
public record XPathOptions(Map<String, String> namespaces, boolean strictNamespaces) implements Serializable {

    public XPathOptions {
        namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
    }

    public static XPathOptions defaults() {
        return new XPathOptions(Map.of(), false);
    }
}
