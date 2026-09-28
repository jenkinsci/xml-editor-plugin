package io.jenkins.plugins.xmleditor.core.validate;

import java.io.Serializable;
import java.util.List;

/** Outcome of a validation: {@code valid} is false when there is at least one ERROR or FATAL issue. */
public record ValidationResult(boolean valid, List<ValidationIssue> issues) implements Serializable {

    public ValidationResult {
        issues = List.copyOf(issues);
    }
}
