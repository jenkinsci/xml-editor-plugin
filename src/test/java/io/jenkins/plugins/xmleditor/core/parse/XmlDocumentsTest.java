package io.jenkins.plugins.xmleditor.core.parse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.validate.ValidationIssue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmlDocumentsTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parseAndWriteBackIsIdentical() throws Exception {
        byte[] bytes = utf8("<?xml version=\"1.0\"?>\n<a>\n  <!-- c -->\n  <b x='1'/>\n</a>\n");
        LDocument doc = XmlDocuments.parse(bytes, "a.xml", 1000);
        assertArrayEquals(bytes, XmlDocuments.toBytes(doc));
    }

    @Test
    void malformedXmlReportsFileLineAndColumn() {
        XmlEditorException e = assertThrows(
                XmlEditorException.class, () -> XmlDocuments.parse(utf8("<a>\n  <b>\n</a>"), "pom.xml", 1000));
        assertTrue(e.getMessage().startsWith("pom.xml:3:"), e.getMessage());
    }

    @Test
    void undeclaredNamespacePrefixIsRejected() {
        XmlEditorException e =
                assertThrows(XmlEditorException.class, () -> XmlDocuments.parse(utf8("<p:a/>"), "x.xml", 1000));
        assertTrue(e.getMessage().startsWith("x.xml:1:"), e.getMessage());
    }

    @Test
    void tooLargeInputIsRejected() {
        XmlEditorException e = assertThrows(
                XmlEditorException.class, () -> XmlDocuments.parse(utf8("<a>0123456789</a>"), "big.xml", 10));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }

    @Test
    void textSizeLimitCountsUtf8Bytes() {
        String text = "<a>èèèèè</a>"; // 12 characters, 17 bytes in UTF-8
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> XmlDocuments.parseText(text, "text", 15));
        assertTrue(e.getMessage().contains("17 bytes"), e.getMessage());
    }

    @Test
    void textWithADeclaredNonUtf8EncodingKeepsItsCharacters() throws Exception {
        LDocument doc =
                XmlDocuments.parseText("<?xml version='1.0' encoding='ISO-8859-1'?><a>città è</a>", "text", 1000);
        assertEquals(
                "città è",
                ((io.jenkins.plugins.xmleditor.core.model.LText)
                                doc.root().children().get(0))
                        .value());
    }

    @Test
    void checkerCollectsIssuesWithPosition() {
        List<ValidationIssue> issues = WellFormednessChecker.check("<a>\n<b></c>\n</a>");
        assertEquals(1, issues.size());
        assertEquals(2, issues.get(0).line());
        assertEquals("FATAL", issues.get(0).severity());
    }

    @Test
    void checkerAcceptsWellFormedXml() {
        assertTrue(WellFormednessChecker.check("<a xmlns:p='u'><p:b/></a>").isEmpty());
    }
}
