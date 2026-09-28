package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.io.Serializable;
import java.util.List;

/**
 * An edit of workspace files: one {@code file} or all files matching {@code files} (Ant patterns, comma separated)
 * minus {@code excludes}.
 */
public record EditRequest(
        String file,
        String files,
        String excludes,
        String outputFile,
        long maxBytes,
        List<XmlOperation> operations,
        XPathOptions options,
        boolean showDiff,
        boolean dryRun)
        implements Serializable {

    public EditRequest {
        operations = List.copyOf(operations);
    }

    /** Rejects invalid parameter combinations with a clear message. */
    public void validate() throws AbortException {
        if ((file == null) == (files == null)) {
            throw new AbortException("Specify exactly one of 'file', 'files' or 'text'");
        }
        if (files != null && outputFile != null) {
            throw new AbortException("'outputFile' cannot be used with 'files'");
        }
        if (excludes != null && files == null) {
            throw new AbortException("'excludes' can only be used with 'files'");
        }
        if (operations.isEmpty()) {
            throw new AbortException("xmlEdit needs at least one operation");
        }
    }
}
