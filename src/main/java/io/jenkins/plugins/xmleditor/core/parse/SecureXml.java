package io.jenkins.plugins.xmleditor.core.parse;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * The only place where JAXP factories are created. Every factory uses the JDK built-in implementation (not whatever
 * happens to be on the classpath) with secure processing on, no external entities, no external DTDs or schemas and
 * explicit entity expansion limits. A DOCTYPE is allowed, because real project files contain one, but nothing it
 * references is ever loaded.
 */
public final class SecureXml {

    static final String ENTITY_EXPANSION_LIMIT = "10000";
    static final String MAX_GENERAL_ENTITY_SIZE = "1000000";
    static final String TOTAL_ENTITY_SIZE = "5000000";

    private SecureXml() {}

    public static SAXParser newSaxParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory f = SAXParserFactory.newDefaultInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);
        f.setXIncludeAware(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        SAXParser parser = f.newSAXParser();
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        setLimit(parser, "entityExpansionLimit", ENTITY_EXPANSION_LIMIT);
        setLimit(parser, "maxGeneralEntitySizeLimit", MAX_GENERAL_ENTITY_SIZE);
        setLimit(parser, "totalEntitySizeLimit", TOTAL_ENTITY_SIZE);
        return parser;
    }

    /** A builder used only to create empty documents (the XPath shadow DOM); it never parses input. */
    public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newDefaultInstance();
        f.setNamespaceAware(true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f.newDocumentBuilder();
    }

    public static XPathFactory newXPathFactory() throws XPathFactoryConfigurationException {
        XPathFactory f = XPathFactory.newDefaultInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return f;
    }

    /**
     * A schema factory that loads nothing on its own: {@code xs:include}/{@code xs:import} are only resolved through
     * the caller's resource resolver, which must supply the content.
     */
    public static SchemaFactory newSchemaFactory() throws SAXException {
        SchemaFactory f = SchemaFactory.newDefaultInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f;
    }

    private static void setLimit(SAXParser parser, String name, String value) throws SAXException {
        try {
            parser.setProperty("jdk.xml." + name, value);
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            parser.setProperty("http://www.oracle.com/xml/jaxp/properties/" + name, value);
        }
    }
}
