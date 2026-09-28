package io.jenkins.plugins.xmleditor.core.edit;

import java.util.List;

/**
 * Outcome of {@link XmlEditor#apply}.
 *
 * @param before serialized document before the operations
 * @param after serialized document after the operations
 * @param warnings namespace warnings found while evaluating XPath
 */
public record EditResult(
        boolean changed, List<OperationReport> reports, String before, String after, List<String> warnings) {

    public EditResult {
        reports = List.copyOf(reports);
        warnings = List.copyOf(warnings);
    }
}
