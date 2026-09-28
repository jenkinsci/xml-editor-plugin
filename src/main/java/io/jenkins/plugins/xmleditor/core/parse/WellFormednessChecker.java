package io.jenkins.plugins.xmleditor.core.parse;

import io.jenkins.plugins.xmleditor.core.validate.ValidationIssue;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Strict, namespace-aware well-formedness check with the hardened SAX parser. */
public final class WellFormednessChecker {

    private WellFormednessChecker() {}

    /** @return the problems found; empty when the document is well-formed */
    public static List<ValidationIssue> check(String xml) {
        List<ValidationIssue> issues = new ArrayList<>();
        CollectingHandler handler = new CollectingHandler(issues);
        try {
            SecureXml.newSaxParser().parse(new InputSource(new StringReader(xml)), handler);
        } catch (SAXParseException e) {
            if (issues.isEmpty() || !issues.get(issues.size() - 1).message().equals(e.getMessage())) {
                issues.add(issue(e, "FATAL"));
            }
        } catch (SAXException | ParserConfigurationException | IOException e) {
            issues.add(new ValidationIssue(0, 0, "FATAL", String.valueOf(e.getMessage())));
        }
        return issues;
    }

    static ValidationIssue issue(SAXParseException e, String severity) {
        return new ValidationIssue(e.getLineNumber(), e.getColumnNumber(), severity, e.getMessage());
    }

    private static final class CollectingHandler extends DefaultHandler implements ErrorHandler {
        private final List<ValidationIssue> issues;

        CollectingHandler(List<ValidationIssue> issues) {
            this.issues = issues;
        }

        @Override
        public void warning(SAXParseException e) {
            // warnings (e.g. about the DOCTYPE) do not affect well-formedness
        }

        @Override
        public void error(SAXParseException e) {
            issues.add(issue(e, "ERROR"));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            issues.add(issue(e, "FATAL"));
            throw e;
        }
    }
}
