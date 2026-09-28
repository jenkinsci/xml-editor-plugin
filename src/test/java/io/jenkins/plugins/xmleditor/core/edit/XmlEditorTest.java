package io.jenkins.plugins.xmleditor.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmlEditorTest {

    @Test
    void everyOperationReportsTheValuesBeforeTheChange() throws Exception {
        LDocument d = doc("<r><v>1</v><w a=\"x\"/><z/><c>t<!-- c --></c></r>");
        EditResult r = apply(
                d,
                new SetText("/r/v", null, "2"),
                new SetAttribute("//w", null, "a", "y"),
                new SetAttribute("//z", null, "a", "1"),
                new RemoveAttribute("//w", null, "a"),
                new Remove("/r/v", null),
                new AddElement("/r", null, "<n/>", null),
                new SetText("/r/c", null, "t"));
        List<List<String>> old =
                r.reports().stream().map(OperationReport::oldValues).toList();
        assertEquals(List.of("1"), old.get(0));
        assertEquals(List.of("x"), old.get(1));
        assertEquals(java.util.Collections.singletonList(null), old.get(2));
        assertEquals(List.of("y"), old.get(3));
        assertEquals(List.of("2"), old.get(4));
        assertEquals(List.of(), old.get(5));
        assertEquals(List.of("t"), old.get(6), "recorded even when nothing changes");
    }

    @Test
    void namespaceWarningsAreReportedByTheEditor() throws Exception {
        LDocument d = doc("<r><a xmlns:p=\"urn:1\"><p:x>1</p:x></a><b xmlns:p=\"urn:2\"/></r>");
        EditResult r = apply(d, new SetText("//p:x", null, "2"));
        assertEquals(1, r.warnings().size());
    }

    @Test
    void prefixDeclaredByAnAddedFragmentIsUsableByLaterOperations() throws Exception {
        LDocument d = doc("<r><a/></r>");
        apply(d, new AddElement("/r/a", null, "<p:x xmlns:p=\"urn:p\"/>", null), new SetText("//p:x", null, "v"));
        assertEquals("<r><a><p:x xmlns:p=\"urn:p\">v</p:x></a></r>", d.serialize());
    }

    private static LDocument doc(String xml) throws XmlEditorException {
        return XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "t.xml", 100_000);
    }

    private static EditResult apply(LDocument doc, XmlOperation... ops) throws XmlEditorException {
        return XmlEditor.apply(doc, List.of(ops), XPathOptions.defaults());
    }

    @Test
    void noMatchFailsByDefault() throws Exception {
        XmlEditorException e =
                assertThrows(XmlEditorException.class, () -> apply(doc("<a/>"), new SetText("/nope", null, "x")));
        assertTrue(e.getMessage().contains("Operation 1 (setText)"), e.getMessage());
        assertTrue(e.getMessage().contains("/nope"), e.getMessage());
        assertTrue(e.getMessage().contains("matched 0"), e.getMessage());
    }

    @Test
    void expectedOneRejectsTwoMatches() throws Exception {
        XmlEditorException e = assertThrows(
                XmlEditorException.class, () -> apply(doc("<a><b/><b/></a>"), new SetText("//b", "ONE", "x")));
        assertTrue(e.getMessage().contains("matched 2"), e.getMessage());
        assertTrue(e.getMessage().contains("exactly 1"), e.getMessage());
    }

    @Test
    void expectedAnyAndExactCounts() throws Exception {
        EditResult r = apply(
                doc("<a><b/><b/></a>"),
                new SetText("//nope", "ANY", "x"),
                new SetText("//zero", "0", "x"),
                new SetText("//b", "2", "y"));
        assertEquals(
                List.of(0, 0, 2),
                r.reports().stream().map(OperationReport::matched).toList());
        assertThrows(XmlEditorException.class, () -> apply(doc("<a><b/></a>"), new SetText("//b", "2", "y")));
    }

    @Test
    void invalidExpectedValue() {
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> Expectation.parse("SOME"));
        assertTrue(e.getMessage().contains("SOME"), e.getMessage());
        assertThrows(XmlEditorException.class, () -> Expectation.parse("-1"));
    }

    @Test
    void operationsSeeThePreviousResults() throws Exception {
        LDocument d = doc("<a><v>1</v></a>");
        EditResult r =
                apply(d, new SetText("/a/v", null, "2"), new SetAttribute("/a/v[.='2']", null, "changed", "yes"));
        assertEquals("<a><v changed=\"yes\">2</v></a>", d.serialize());
        assertTrue(r.changed());
    }

    @Test
    void unchangedDocumentIsReportedAsSuch() throws Exception {
        LDocument d = doc("<a><v>1</v></a>");
        EditResult r = apply(d, new SetText("/a/v", null, "1"));
        assertFalse(r.changed());
        assertEquals(0, r.reports().get(0).modified());
    }

    @Test
    void setTextOnElementWithChildrenIsRefused() {
        XmlEditorException e =
                assertThrows(XmlEditorException.class, () -> apply(doc("<a><b/></a>"), new SetText("/a", null, "x")));
        assertTrue(e.getMessage().contains("child elements"), e.getMessage());
    }

    @Test
    void setAttributeNeedsAnElementAndAValidName() throws Exception {
        assertThrows(
                XmlEditorException.class, () -> apply(doc("<a x='1'/>"), new SetAttribute("/a/@x", null, "y", "1")));
        assertThrows(XmlEditorException.class, () -> apply(doc("<a/>"), new SetAttribute("/a", null, "1bad", "1")));
        XmlEditorException e = assertThrows(
                XmlEditorException.class, () -> apply(doc("<a/>"), new SetAttribute("/a", null, "p:x", "1")));
        assertTrue(e.getMessage().contains("p"), e.getMessage());
    }

    @Test
    void setTextRefusesComments() {
        assertThrows(
                XmlEditorException.class, () -> apply(doc("<a><!--c--></a>"), new SetText("//comment()", null, "x")));
    }

    @Test
    void newTextUsesTheFileLineSeparatorForNewlines() throws Exception {
        LDocument d = doc("<a>\r\n<b>x</b>\r\n</a>");
        apply(d, new SetText("//b", null, "l1\nl2"));
        assertEquals("<a>\r\n<b>l1\r\nl2</b>\r\n</a>", d.serialize());
    }
}
