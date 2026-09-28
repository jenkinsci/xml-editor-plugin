package io.jenkins.plugins.xmleditor.core.validate;

import java.io.Serializable;

/**
 * A problem found while checking or validating a document.
 *
 * @param severity {@code WARNING}, {@code ERROR} or {@code FATAL}
 */
public record ValidationIssue(int line, int column, String severity, String message) implements Serializable {

    @Override
    public String toString() {
        return line + ":" + column + ": " + message;
    }
}
