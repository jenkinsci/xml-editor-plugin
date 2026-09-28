package io.jenkins.plugins.xmleditor.core.xpath;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LComment;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LParent;
import io.jenkins.plugins.xmleditor.core.model.LProcessingInstruction;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.parse.SecureXml;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A W3C DOM copy of the lossless tree used only to evaluate XPath with the JDK engine. Every DOM node carries a
 * back-link to the lossless node it was created from.
 */
final class ShadowDom {

    private static final String KEY = "io.jenkins.plugins.xmleditor.node";

    private final Document dom;
    private final boolean transparentDefaultNamespace;

    ShadowDom(LDocument doc, boolean transparentDefaultNamespace) throws XmlEditorException {
        this.transparentDefaultNamespace = transparentDefaultNamespace;
        try {
            this.dom = SecureXml.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("cannot create the XPath document", e);
        }
        try {
            appendChildren(dom, doc);
        } catch (DOMException e) {
            throw new XmlEditorException("Cannot evaluate XPath on this document: " + e.getMessage(), e);
        }
    }

    Document dom() {
        return dom;
    }

    /** The lossless node or attribute a DOM node was created from, or {@code null}. */
    static LItem item(Node node) {
        return (LItem) node.getUserData(KEY);
    }

    private void appendChildren(Node target, LParent source) {
        boolean documentLevel = target == dom;
        for (LNode child : source.children()) {
            Node created = null;
            if (child instanceof LElement e) {
                created = element(e);
            } else if (child instanceof LComment c) {
                created = dom.createComment(c.text());
            } else if (child instanceof LProcessingInstruction pi) {
                created = dom.createProcessingInstruction(pi.target(), pi.data());
            } else if (!documentLevel && child instanceof LText t) {
                created = dom.createTextNode(t.value());
            } else if (!documentLevel && child instanceof LCData cd) {
                created = dom.createCDATASection(cd.value());
            }
            if (created != null) {
                created.setUserData(KEY, child, null);
                target.appendChild(created);
            }
        }
    }

    private Element element(LElement e) {
        String prefix = e.prefix();
        String uri = e.namespaceUri(prefix);
        Element element;
        if (prefix.isEmpty()) {
            boolean noNamespace = transparentDefaultNamespace || uri == null || uri.isEmpty();
            element = dom.createElementNS(noNamespace ? null : uri, e.localName());
        } else {
            element = dom.createElementNS(uri, e.name());
        }
        for (LAttribute a : e.attributes()) {
            if (a.isNamespaceDeclaration()) {
                continue;
            }
            String attrUri = a.prefix().isEmpty() ? null : e.namespaceUri(a.prefix());
            element.setAttributeNS(attrUri, a.name(), a.value());
            Attr attr = attrUri == null
                    ? element.getAttributeNode(a.name())
                    : element.getAttributeNodeNS(attrUri, a.localName());
            attr.setUserData(KEY, a, null);
        }
        appendChildren(element, e);
        return element;
    }
}
