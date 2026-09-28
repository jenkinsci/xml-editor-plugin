package io.jenkins.plugins.xmleditor.core.validate;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.parse.SecureXml;
import io.jenkins.plugins.xmleditor.core.parse.WellFormednessChecker;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Well-formedness and XSD validation. Schema includes/imports are only read from files inside the workspace. */
public final class XmlValidator {

    static final int MAX_ISSUES = 1000;

    private XmlValidator() {}

    /**
     * @param schema XSD file, or {@code null} to check well-formedness only
     * @param allowedRoot directory that {@code xs:include}/{@code xs:import} may read from
     * @throws XmlEditorException when the schema itself cannot be loaded
     */
    public static ValidationResult validate(String xml, Path schema, Path allowedRoot) throws XmlEditorException {
        List<ValidationIssue> wellFormedness = WellFormednessChecker.check(xml);
        if (!wellFormedness.isEmpty() || schema == null) {
            return new ValidationResult(wellFormedness.isEmpty(), wellFormedness);
        }
        Schema compiled = loadSchema(schema, allowedRoot);
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            Validator validator = compiled.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new Collector(issues));
            validator.validate(
                    new SAXSource(SecureXml.newSaxParser().getXMLReader(), new InputSource(new StringReader(xml))));
        } catch (SAXParseException e) {
            // already collected by the error handler
        } catch (SAXException | ParserConfigurationException | IOException e) {
            issues.add(new ValidationIssue(0, 0, "FATAL", String.valueOf(e.getMessage())));
        }
        boolean valid = issues.stream().noneMatch(i -> !i.severity().equals("WARNING"));
        return new ValidationResult(valid, issues);
    }

    private static Schema loadSchema(Path schema, Path allowedRoot) throws XmlEditorException {
        String name = String.valueOf(schema.getFileName());
        try {
            SchemaFactory factory = SecureXml.newSchemaFactory();
            factory.setResourceResolver(new WorkspaceResolver(allowedRoot.toRealPath()));
            return factory.newSchema(new StreamSource(schema.toFile()));
        } catch (SAXException | RuntimeException e) {
            Throwable cause = e instanceof SAXException && e.getCause() != null ? e.getCause() : e;
            throw new XmlEditorException("Cannot load schema " + name + ": " + cause.getMessage(), e);
        } catch (IOException e) {
            throw new XmlEditorException("Cannot read schema " + name + ": " + e.getMessage(), e);
        }
    }

    /** Serves schema documents from the workspace only; everything else is refused. */
    private static final class WorkspaceResolver implements LSResourceResolver {

        private final Path root;
        private final DOMImplementationLS ls;

        WorkspaceResolver(Path root) {
            this.root = root;
            try {
                this.ls = (DOMImplementationLS) SecureXml.newDocumentBuilder().getDOMImplementation();
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public LSInput resolveResource(
                String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            if (systemId == null) {
                throw new IllegalArgumentException(
                        "schema reference without location (namespace " + namespaceURI + ")");
            }
            URI target;
            try {
                target = baseURI == null ? new URI(systemId) : new URI(baseURI).resolve(new URI(systemId));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("invalid schema location '" + systemId + "'", e);
            }
            if (!"file".equalsIgnoreCase(target.getScheme())) {
                throw new IllegalArgumentException("schema location '" + systemId
                        + "' is not allowed: only files inside the workspace can be used");
            }
            try {
                Path path = Path.of(target).toRealPath();
                if (!path.startsWith(root)) {
                    throw new IllegalArgumentException(
                            "schema location '" + systemId + "' points outside the workspace");
                }
                LSInput input = ls.createLSInput();
                input.setSystemId(target.toString());
                input.setPublicId(publicId);
                input.setByteStream(new ByteArrayInputStream(Files.readAllBytes(path)));
                return input;
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read schema '" + systemId + "': " + e.getMessage(), e);
            }
        }
    }

    private static final class Collector implements ErrorHandler {
        private final List<ValidationIssue> issues;

        Collector(List<ValidationIssue> issues) {
            this.issues = issues;
        }

        private void add(SAXParseException e, String severity) {
            if (issues.size() < MAX_ISSUES) {
                issues.add(new ValidationIssue(e.getLineNumber(), e.getColumnNumber(), severity, e.getMessage()));
            }
        }

        @Override
        public void warning(SAXParseException e) {
            add(e, "WARNING");
        }

        @Override
        public void error(SAXParseException e) {
            add(e, "ERROR");
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            add(e, "FATAL");
            throw e;
        }
    }
}
