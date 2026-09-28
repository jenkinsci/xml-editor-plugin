package io.jenkins.plugins.xmleditor.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LCData;
import io.jenkins.plugins.xmleditor.core.model.LComment;
import io.jenkins.plugins.xmleditor.core.model.LDoctype;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LNode;
import io.jenkins.plugins.xmleditor.core.model.LProcessingInstruction;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.model.LXmlDeclaration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LosslessParserTest {

    private static LDocument parse(String xml) throws XmlEditorException {
        LDocument doc = LosslessParser.parse(XmlSource.decode(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(xml, doc.serialize(), "round-trip must be identical");
        return doc;
    }

    private static List<LElement> childElements(LElement e) {
        return e.children().stream()
                .filter(LElement.class::isInstance)
                .map(LElement.class::cast)
                .toList();
    }

    @Test
    void elementsAndAttributesKeepTheirExactSyntax() throws Exception {
        LDocument doc = parse("<a x=\"1\" y='2'  z = \"3\"><b/><c /></a >");
        LElement a = doc.root();
        assertEquals("a", a.name());
        List<LAttribute> attrs = a.attributes();
        assertEquals(3, attrs.size());
        assertEquals('"', attrs.get(0).quote());
        assertEquals('\'', attrs.get(1).quote());
        assertEquals(" = ", attrs.get(2).equalsRaw());
        assertEquals("  ", attrs.get(2).leadingWhitespace());
        assertEquals("3", attrs.get(2).value());
        assertEquals(a, attrs.get(2).owner());
        assertEquals("</a >", a.endTag());
        List<LElement> kids = childElements(a);
        assertTrue(kids.get(0).selfClosing());
        assertEquals("", kids.get(0).startTagTail());
        assertTrue(kids.get(1).selfClosing());
        assertEquals(" ", kids.get(1).startTagTail());
        assertNull(kids.get(1).endTag());
        assertEquals(a, kids.get(1).parent());
    }

    @Test
    void attributesOnSeveralLines() throws Exception {
        LDocument doc = parse("<a\n    x=\"1\"\n    y=\"2\"\n>text</a>");
        assertEquals("\n    ", doc.root().attributes().get(1).leadingWhitespace());
        assertEquals("\n", doc.root().startTagTail());
        assertEquals("2", doc.root().attribute("y").orElseThrow().value());
    }

    @Test
    void textValueDecodesPredefinedAndCharacterReferences() throws Exception {
        LDocument doc = parse("<a>1 &lt; 2 &amp;&#65;&#x42;&quot;&apos;&gt; x > y</a>");
        LText text = assertInstanceOf(LText.class, doc.root().children().get(0));
        assertEquals("1 &lt; 2 &amp;&#65;&#x42;&quot;&apos;&gt; x > y", text.raw());
        assertEquals("1 < 2 &AB\"'> x > y", text.value());
    }

    @Test
    void textValueNormalizesLineEndings() throws Exception {
        LDocument doc = parse("<a>x\r\ny\rz</a>");
        assertEquals("x\ny\nz", ((LText) doc.root().children().get(0)).value());
    }

    @Test
    void attributeValueIsNormalized() throws Exception {
        LDocument doc = parse("<a x=\"a&#10;b\tc&amp;\"/>");
        assertEquals("a\nb c&", doc.root().attributes().get(0).value());
        assertEquals("a&#10;b\tc&amp;", doc.root().attributes().get(0).rawValue());
    }

    @Test
    void cdataCommentsAndProcessingInstructionsAreNodes() throws Exception {
        LDocument doc = parse("<a><![CDATA[<x> & ]]><!-- c > - --><?pi data?></a>");
        List<LNode> kids = doc.root().children();
        assertEquals(
                "<![CDATA[<x> & ]]>",
                assertInstanceOf(LCData.class, kids.get(0)).raw());
        assertEquals("<x> & ", ((LCData) kids.get(0)).value());
        assertEquals(
                "<!-- c > - -->", assertInstanceOf(LComment.class, kids.get(1)).raw());
        assertEquals(
                "<?pi data?>",
                assertInstanceOf(LProcessingInstruction.class, kids.get(2)).raw());
    }

    @Test
    void prologAndEpilogAreKept() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n<!-- head -->\n<?style x?>\n<root/>\n<!-- tail -->\n";
        LDocument doc = parse(xml);
        List<LNode> kids = doc.children();
        assertInstanceOf(LXmlDeclaration.class, kids.get(0));
        assertInstanceOf(LText.class, kids.get(1));
        assertInstanceOf(LComment.class, kids.get(2));
        assertEquals("root", doc.root().name());
        assertInstanceOf(LComment.class, kids.get(kids.size() - 2));
    }

    @Test
    void doctypeWithInternalSubsetIsOpaqueAndEntitiesAreKnown() throws Exception {
        String xml =
                "<!DOCTYPE r [\n  <!ENTITY e \"x]y>\">\n  <!-- ] > -->\n  <!ENTITY ext SYSTEM \"file:///etc/passwd\">\n]>\n"
                        + "<r>&e;/&ext;</r>";
        LDocument doc = parse(xml);
        LDoctype doctype = assertInstanceOf(LDoctype.class, doc.children().get(0));
        assertTrue(doctype.raw().startsWith("<!DOCTYPE r ["));
        assertTrue(doctype.raw().endsWith("]>"));
        assertEquals("x]y>", doc.internalEntities().get("e"));
        assertFalse(doc.internalEntities().containsKey("ext"));
        assertEquals("x]y>/&ext;", ((LText) doc.root().children().get(0)).value());
    }

    @Test
    void doctypeWithPublicIdAndNoSubset() throws Exception {
        LDocument doc = parse(
                "<!DOCTYPE web-app PUBLIC \"-//Sun//DTD Web Application 2.3//EN\" \"http://java.sun.com/dtd/web-app_2_3.dtd\">\n<web-app/>");
        assertInstanceOf(LDoctype.class, doc.children().get(0));
        assertEquals("web-app", doc.root().name());
    }

    @Test
    void namespacesAreResolvedFromScope() throws Exception {
        LDocument doc = parse("<p:a xmlns:p=\"u1\" xmlns=\"u0\"><b><c xmlns=\"\"/></b></p:a>");
        LElement a = doc.root();
        assertEquals("p", a.prefix());
        assertEquals("a", a.localName());
        LElement b = childElements(a).get(0);
        assertEquals("", b.prefix());
        assertEquals("u0", b.namespaceUri(""));
        assertEquals("u1", b.namespaceUri("p"));
        LElement c = childElements(b).get(0);
        assertEquals("", c.namespaceUri(""));
        assertNull(c.namespaceUri("q"));
    }

    @Test
    void whitespaceOnlyTextIsRecognized() throws Exception {
        LDocument doc = parse("<a>\n  <b/>\n</a>");
        assertTrue(((LText) doc.root().children().get(0)).isWhitespace());
    }

    @Test
    void fragmentsCanHoldSeveralNodes() throws Exception {
        List<LNode> nodes = LosslessParser.parseFragment("<a>1</a><!-- c --><b/>");
        assertEquals(3, nodes.size());
        assertEquals("a", ((LElement) nodes.get(0)).name());
    }

    @Test
    void malformedInputIsRejected() {
        assertThrows(XmlEditorException.class, () -> LosslessParser.parseFragment("<a><b></a>"));
        assertThrows(XmlEditorException.class, () -> LosslessParser.parseFragment("<a x=1/>"));
        assertThrows(XmlEditorException.class, () -> LosslessParser.parseFragment("<a>"));
    }
}
