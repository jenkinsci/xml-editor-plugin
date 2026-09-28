package io.jenkins.plugins.xmleditor.core.xpath;

import io.jenkins.plugins.xmleditor.core.parse.SecureXml;
import java.util.Iterator;
import java.util.Optional;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;

/** Syntax check of an XPath 1.0 expression without a document (namespace prefixes are accepted as they are). */
public final class XPathSyntax {

    private static final NamespaceContext ANY_PREFIX = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return "urn:xml-editor:any:" + prefix;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return java.util.Collections.emptyIterator();
        }
    };

    private XPathSyntax() {}

    /** @return the error message, or empty when the expression compiles */
    public static Optional<String> check(String expression) {
        try {
            XPath xpath = SecureXml.newXPathFactory().newXPath();
            xpath.setNamespaceContext(ANY_PREFIX);
            xpath.compile(expression);
            return Optional.empty();
        } catch (XPathExpressionException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String message = root.getMessage() != null ? root.getMessage() : e.getMessage();
            return Optional.of("Invalid XPath: " + (message != null ? message : "syntax error"));
        } catch (XPathFactoryConfigurationException e) {
            throw new IllegalStateException("cannot create the XPath engine", e);
        }
    }
}
