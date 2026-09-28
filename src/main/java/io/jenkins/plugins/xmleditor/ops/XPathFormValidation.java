package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Item;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.core.xpath.XPathSyntax;
import jenkins.model.Jenkins;

/** Live syntax check of XPath fields in forms (Freestyle configuration and Snippet Generator). */
public final class XPathFormValidation {

    private XPathFormValidation() {}

    /**
     * @param item the job being configured, or {@code null} outside a job (e.g. the global Snippet Generator)
     * @param required whether an empty value is an error
     */
    public static FormValidation check(@CheckForNull Item item, String value, boolean required) {
        if (item == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER) : !item.hasPermission(Item.CONFIGURE)) {
            return FormValidation.ok();
        }
        if (value == null || value.isBlank()) {
            return required ? FormValidation.error("XPath is required") : FormValidation.ok();
        }
        if (value.contains("$")) {
            return FormValidation.ok(); // variables are expanded at run time
        }
        return XPathSyntax.check(value).map(FormValidation::error).orElse(FormValidation.ok());
    }
}
