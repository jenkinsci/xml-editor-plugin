package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.xpath.XPathEngine;
import java.io.Serializable;

/** One modification applied to every node selected by {@link #xpath()}. */
public abstract class XmlOperation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String xpath;
    private final String expected;

    protected XmlOperation(String xpath, String expected) {
        this.xpath = xpath;
        this.expected = expected;
    }

    /** Name used in reports and messages, e.g. {@code setText}. */
    public abstract String type();

    public String xpath() {
        return xpath;
    }

    public String expected() {
        return expected;
    }

    /**
     * Applies the operation to one selected node.
     *
     * @return {@code true} when the document actually changed
     */
    protected abstract boolean applyTo(LItem target, EditContext context) throws XmlEditorException;

    /** Expectation used when the user did not set {@code expected}; {@code null} means {@code AT_LEAST_ONE}. */
    protected String defaultExpected() {
        return null;
    }

    /**
     * Called when the XPath matched nothing (and the expectation allowed it).
     *
     * @return {@code true} when the document changed
     */
    protected boolean applyWhenNoMatch(EditContext context, XPathEngine engine) throws XmlEditorException {
        return false;
    }

    protected XmlEditorException unsupported(LItem target) {
        return new XmlEditorException(type() + " cannot be applied to " + EditContext.describe(target)
                + " selected by XPath '" + xpath + "'");
    }
}
