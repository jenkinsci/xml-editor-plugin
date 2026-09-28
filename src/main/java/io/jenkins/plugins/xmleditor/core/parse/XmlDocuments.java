package io.jenkins.plugins.xmleditor.core.parse;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.validate.ValidationIssue;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Entry point for turning file bytes into a lossless document and back. */
public final class XmlDocuments {

    private XmlDocuments() {}

    /**
     * Decodes, checks well-formedness (securely) and builds the lossless tree.
     *
     * @param displayName used as prefix of error messages, e.g. {@code pom.xml:12:5: ...}
     */
    public static LDocument parse(byte[] bytes, String displayName, long maxBytes) throws XmlEditorException {
        checkSize(bytes.length, displayName, maxBytes);
        XmlSource source;
        try {
            source = XmlSource.decode(bytes);
        } catch (XmlEditorException e) {
            throw new XmlEditorException(displayName + ": " + e.getMessage(), e);
        }
        return parse(source, displayName);
    }

    /**
     * Parses XML that is already text (e.g. the {@code text:} parameter of a step): the encoding declared in it is
     * ignored, since there are no bytes to decode.
     */
    public static LDocument parseText(String text, String displayName, long maxBytes) throws XmlEditorException {
        checkSize(text.getBytes(StandardCharsets.UTF_8).length, displayName, maxBytes);
        return parse(XmlSource.ofText(text), displayName);
    }

    public static byte[] toBytes(LDocument doc) throws XmlEditorException {
        return doc.source().encode(doc.serialize());
    }

    private static void checkSize(long size, String displayName, long maxBytes) throws XmlEditorException {
        if (size > maxBytes) {
            throw new XmlEditorException(displayName + " is too large (" + size + " bytes, limit " + maxBytes
                    + "); raise maxSizeMb if this is expected");
        }
    }

    private static LDocument parse(XmlSource source, String displayName) throws XmlEditorException {
        List<ValidationIssue> issues = WellFormednessChecker.check(source.text());
        if (!issues.isEmpty()) {
            ValidationIssue first = issues.get(0);
            throw new XmlEditorException(
                    displayName + ":" + first.line() + ":" + first.column() + ": " + first.message());
        }
        try {
            return LosslessParser.parse(source);
        } catch (XmlEditorException e) {
            throw new XmlEditorException(displayName + ": " + e.getMessage(), e);
        }
    }
}
