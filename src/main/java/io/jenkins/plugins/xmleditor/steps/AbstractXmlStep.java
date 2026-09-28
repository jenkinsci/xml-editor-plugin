package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

/** Options shared by all XML steps: namespaces and size limit. */
public abstract class AbstractXmlStep extends Step {

    static final int DEFAULT_MAX_SIZE_MB = 50;

    private Map<String, String> namespaces = new LinkedHashMap<>();
    private boolean strictNamespaces;
    private int maxSizeMb = DEFAULT_MAX_SIZE_MB;

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

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    @DataBoundSetter
    public void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    XPathOptions xpathOptions() {
        return new XPathOptions(namespaces, strictNamespaces);
    }

    long maxBytes() throws AbortException {
        if (maxSizeMb <= 0) {
            throw new AbortException("maxSizeMb must be greater than 0");
        }
        return maxSizeMb * 1024L * 1024L;
    }
}
