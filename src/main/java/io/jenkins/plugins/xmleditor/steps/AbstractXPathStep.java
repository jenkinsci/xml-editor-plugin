package io.jenkins.plugins.xmleditor.steps;

import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.kohsuke.stapler.DataBoundSetter;

/** Steps that evaluate XPath expressions: namespace prefixes and strict namespace handling. */
public abstract class AbstractXPathStep extends AbstractXmlStep {

    private Map<String, String> namespaces = new LinkedHashMap<>();
    private boolean strictNamespaces;

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    @DataBoundSetter
    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces == null ? new LinkedHashMap<>() : new LinkedHashMap<>(namespaces);
    }

    public boolean isStrictNamespaces() {
        return strictNamespaces;
    }

    @DataBoundSetter
    public void setStrictNamespaces(boolean strictNamespaces) {
        this.strictNamespaces = strictNamespaces;
    }

    XPathOptions xpathOptions() {
        return new XPathOptions(namespaces, strictNamespaces);
    }
}
