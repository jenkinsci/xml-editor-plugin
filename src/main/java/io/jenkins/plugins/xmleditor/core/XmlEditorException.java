package io.jenkins.plugins.xmleditor.core;

/**
 * A problem caused by the user's input (file, XPath, operation parameters), reported with a clear message.
 * Jenkins adapters turn it into an {@code AbortException} so that no stack trace is shown in the build log.
 */
public class XmlEditorException extends Exception {

    private static final long serialVersionUID = 1L;

    public XmlEditorException(String message) {
        super(message);
    }

    public XmlEditorException(String message, Throwable cause) {
        super(message, cause);
    }
}
