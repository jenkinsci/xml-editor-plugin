package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Live check of the alternative fields that select the document ({@code file}, {@code files}, {@code text}), so that
 * the Snippet Generator and the job configuration report a conflict before the build runs.
 */
public final class SourceFormValidation {

    private SourceFormValidation() {}

    /**
     * @param item the job being configured, or {@code null} outside a job (e.g. the global Snippet Generator)
     * @param alternatives the alternative fields for the message, e.g. {@code 'file' or 'text'}
     * @param value the value of the field being checked
     * @param others the values of the other alternative fields
     * @return an error when the field and any other alternative are both set
     */
    public static FormValidation onlyOne(@CheckForNull Item item, String alternatives, String value, String... others) {
        // Same rule as XPathFormValidation: feedback only for users who can configure the job (admins outside a job).
        if (item == null ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER) : !item.hasPermission(Item.CONFIGURE)) {
            return FormValidation.ok();
        }
        if (Util.fixEmptyAndTrim(value) == null) {
            return FormValidation.ok();
        }
        for (String other : others) {
            if (Util.fixEmptyAndTrim(other) != null) {
                return FormValidation.error("Use only one of " + alternatives);
            }
        }
        return FormValidation.ok();
    }
}
