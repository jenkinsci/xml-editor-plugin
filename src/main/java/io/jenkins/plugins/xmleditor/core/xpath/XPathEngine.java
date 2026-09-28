package io.jenkins.plugins.xmleditor.core.xpath;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.parse.SecureXml;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;
import org.w3c.dom.NodeList;

/** Evaluates XPath 1.0 expressions on a lossless document and maps selected nodes back to it. */
public final class XPathEngine {

    private final LDocument doc;
    private final XPathOptions options;
    private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
    private NamespaceBinder binder;
    private final XPath xpath;
    private ShadowDom shadow;

    public XPathEngine(LDocument doc, XPathOptions options) throws XmlEditorException {
        this.doc = doc;
        this.options = options;
        this.binder = new NamespaceBinder(doc, options);
        warnings.addAll(binder.warnings());
        try {
            this.xpath = SecureXml.newXPathFactory().newXPath();
        } catch (XPathFactoryConfigurationException e) {
            throw new IllegalStateException("cannot create the XPath engine", e);
        }
        xpath.setNamespaceContext(binder);
        this.shadow = new ShadowDom(doc, !binder.strict());
    }

    /** Rebuilds the XPath view after the document has been modified, including namespace prefixes it declares. */
    public void refresh() throws XmlEditorException {
        this.binder = new NamespaceBinder(doc, options);
        warnings.addAll(binder.warnings());
        xpath.setNamespaceContext(binder);
        this.shadow = new ShadowDom(doc, !binder.strict());
    }

    public List<String> warnings() {
        return new ArrayList<>(warnings);
    }

    /** Nodes selected by {@code expression}: elements, attributes, text, CDATA, comments or PIs. */
    public List<LItem> selectNodes(String expression) throws XmlEditorException {
        NodeList nodes = nodeSet(compile(expression), expression);
        List<LItem> items = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            LItem item = ShadowDom.item(nodes.item(i));
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * String value of the first selected node, or the string result of a non node-set expression.
     *
     * @return empty when the expression selects no node
     */
    public Optional<String> evaluateString(String expression) throws XmlEditorException {
        XPathExpression compiled = compile(expression);
        NodeList nodes = tryNodeSet(compiled);
        if (nodes == null) {
            return Optional.of((String) evaluate(compiled, XPathConstants.STRING, expression));
        }
        return nodes.getLength() == 0
                ? Optional.empty()
                : Optional.of(nodes.item(0).getTextContent());
    }

    /** String values of all selected nodes (a single element list for non node-set expressions). */
    public List<String> evaluateList(String expression) throws XmlEditorException {
        XPathExpression compiled = compile(expression);
        NodeList nodes = tryNodeSet(compiled);
        if (nodes == null) {
            return List.of((String) evaluate(compiled, XPathConstants.STRING, expression));
        }
        List<String> values = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent());
        }
        return values;
    }

    public double evaluateNumber(String expression) throws XmlEditorException {
        return (Double) evaluate(compile(expression), XPathConstants.NUMBER, expression);
    }

    public boolean evaluateBoolean(String expression) throws XmlEditorException {
        return (Boolean) evaluate(compile(expression), XPathConstants.BOOLEAN, expression);
    }

    private XPathExpression compile(String expression) throws XmlEditorException {
        binder.checkPrefixes(expression);
        try {
            return xpath.compile(expression);
        } catch (XPathExpressionException e) {
            throw new XmlEditorException("Invalid XPath expression '" + expression + "': " + rootMessage(e), e);
        }
    }

    private NodeList nodeSet(XPathExpression compiled, String expression) throws XmlEditorException {
        NodeList nodes = tryNodeSet(compiled);
        if (nodes == null) {
            throw new XmlEditorException("XPath '" + expression + "' must select nodes (it returns a value)");
        }
        return nodes;
    }

    private NodeList tryNodeSet(XPathExpression compiled) {
        try {
            return (NodeList) compiled.evaluate(shadow.dom(), XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            return null;
        }
    }

    private Object evaluate(XPathExpression compiled, QName type, String expression) throws XmlEditorException {
        try {
            return compiled.evaluate(shadow.dom(), type);
        } catch (XPathExpressionException e) {
            throw new XmlEditorException("Cannot evaluate XPath '" + expression + "': " + rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage() != null ? root.getMessage() : t.getMessage());
    }
}
