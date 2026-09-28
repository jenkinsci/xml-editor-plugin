package io.jenkins.plugins.xmleditor.core.edit;

import static io.jenkins.plugins.xmleditor.core.edit.GoldenSupport.assertGolden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** addElement and remove: byte-for-byte expectations and error cases. */
class GoldenStructureTest {

    private static final String DEP_COMPACT = "<dependency><groupId>g</groupId><artifactId>b</artifactId></dependency>";

    @Test
    void compactFragmentIsFormattedLikeItsSiblingsInCrlfFile() throws Exception {
        assertGolden("addelement-compact-crlf", new AddElement("/project/dependencies", null, DEP_COMPACT, null));
    }

    @Test
    void multiLineFragmentIsReindented() throws Exception {
        String fragment = "\n              <dependency>\n                  <artifactId>c</artifactId>\n"
                + "                  <!-- user comment -->\n              </dependency>\n          ";
        assertGolden("addelement-multiline", new AddElement("/project/dependencies", null, fragment, "LAST_CHILD"));
    }

    @Test
    void selfClosingParentIsOpened() throws Exception {
        assertGolden("addelement-selfclosing-parent", new AddElement("//deps", null, "<d/>", null));
    }

    @Test
    void emptyParentWithTabIndentation() throws Exception {
        assertGolden("addelement-empty-parent-tabs", new AddElement("//deps", null, "<d/>", null));
    }

    @Test
    void allPositionsKeepCommentsInPlace() throws Exception {
        assertGolden(
                "addelement-positions",
                new AddElement("/r", null, "<first/>", "FIRST_CHILD"),
                new AddElement("//b", null, "<beforeB/>", "BEFORE"),
                new AddElement("//a", null, "<afterA/>", "after"));
    }

    @Test
    void singleLineDocumentStaysOnOneLine() throws Exception {
        assertGolden("addelement-inline", new AddElement("/r", null, "<b><c/></b>", null));
    }

    @Test
    void removedElementsLeaveNoBlankLines() throws Exception {
        assertGolden("remove-lines-crlf", new Remove("//b", null), new Remove("//a", null));
    }

    @Test
    void nestedMatchesAreRemovedOnce() throws Exception {
        EditResult r = assertGolden("remove-nested", new Remove("//x", null));
        assertEquals(
                new OperationReport("remove", "//x", 2, 1, List.of("")),
                r.reports().get(0));
    }

    @Test
    void removeInlineNodesAndAttributes() throws Exception {
        assertGolden("remove-inline-and-attribute", new Remove("//a/@k", null), new Remove("//b", null));
    }

    @Test
    void removeLastChildKeepsTheClosingTagIndentation() throws Exception {
        assertGolden("remove-last-child", new Remove("//d2", null));
    }

    private static XmlEditorException failure(String xml, XmlOperation op) throws XmlEditorException {
        LDocument doc = XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "t.xml", 10_000);
        return assertThrows(XmlEditorException.class, () -> XmlEditor.apply(doc, List.of(op), XPathOptions.defaults()));
    }

    @Test
    void errorCases() throws Exception {
        assertTrue(failure("<r/>", new AddElement("/r", null, "<x/>", "BEFORE"))
                .getMessage()
                .contains("root"));
        assertTrue(failure("<r/>", new Remove("/r", null)).getMessage().contains("root"));
        assertTrue(failure("<r/>", new AddElement("/r", null, "<p:x/>", null))
                .getMessage()
                .contains("'p'"));
        assertTrue(failure("<r/>", new AddElement("/r", null, "<x>", null))
                .getMessage()
                .contains("fragment"));
        assertTrue(failure("<r/>", new AddElement("/r", null, "<x/>", "INSIDE"))
                .getMessage()
                .contains("INSIDE"));
        assertTrue(failure("<r/>", new AddElement("/r", null, "  ", null))
                .getMessage()
                .contains("fragment"));
        assertTrue(failure("<r a='1'/>", new AddElement("/r/@a", null, "<x/>", null))
                .getMessage()
                .contains("@a"));
    }

    @Test
    void prefixesDeclaredInTheFragmentOrInScopeAreAccepted() throws Exception {
        LDocument doc =
                XmlDocuments.parse("<r xmlns:q='urn:q'><a/></r>".getBytes(StandardCharsets.UTF_8), "t.xml", 10_000);
        XmlEditor.apply(
                doc,
                List.of(new AddElement("//a", null, "<p:x xmlns:p='urn:p'/><q:y/>", null)),
                XPathOptions.defaults());
        assertEquals("<r xmlns:q='urn:q'><a><p:x xmlns:p='urn:p'/><q:y/></a></r>", doc.serialize());
    }
}
