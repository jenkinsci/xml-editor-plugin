package io.jenkins.plugins.xmleditor.core.edit;

import static io.jenkins.plugins.xmleditor.core.edit.GoldenSupport.assertGolden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Byte-for-byte expectations for setText, setAttribute and removeAttribute. */
class GoldenEditTest {

    @Test
    void pomVersionBumpKeepsEverythingElse() throws Exception {
        EditResult r = assertGolden("pom-version-bump", new SetText("/project/version", null, "2.4.0"));
        assertTrue(r.changed());
        assertEquals(
                new OperationReport("setText", "/project/version", 1, 1, java.util.List.of("2.3.4")),
                r.reports().get(0));
    }

    @Test
    void setTextOpensSelfClosingElements() throws Exception {
        assertGolden(
                "settext-selfclosing",
                new SetText("//b", null, "v"),
                new SetText("//c", null, "w"),
                new SetText("//d", null, "z"));
    }

    @Test
    void setTextKeepsCdataWhenPossible() throws Exception {
        assertGolden(
                "settext-cdata", new SetText("//s", null, "new <x> & y"), new SetText("//t", null, "has ]]> inside"));
    }

    @Test
    void setTextOnAttributesKeepsTheQuote() throws Exception {
        assertGolden(
                "settext-attribute", new SetText("/a/@k", null, "it's & <"), new SetText("/a/@j", null, "say \"hi\""));
    }

    @Test
    void setTextEscapesMarkup() throws Exception {
        assertGolden("settext-escape", new SetText("/a", null, "a<b&c>d"));
    }

    @Test
    void setTextKeepsComments() throws Exception {
        assertGolden("settext-keeps-comments", new SetText("/a", null, "new"));
    }

    @Test
    void setTextOnATextNode() throws Exception {
        assertGolden("settext-text-node", new SetText("/a/text()[2]", null, "2"));
    }

    @Test
    void setAttributeUpdatesOrAddsFollowingTheLayout() throws Exception {
        EditResult r = assertGolden(
                "setattribute",
                new SetAttribute("//x", null, "a", "2"),
                new SetAttribute("//x", null, "n", "v"),
                new SetAttribute("//y", null, "c", "3"),
                new SetAttribute("//z", null, "q", "1"),
                new SetAttribute("//w", null, "a", "1"));
        assertEquals(0, r.reports().get(4).modified(), "same value: nothing rewritten");
    }

    @Test
    void removeAttribute() throws Exception {
        EditResult r = assertGolden(
                "removeattribute",
                new RemoveAttribute("//x", null, "b"),
                new RemoveAttribute("//y", null, "b"),
                new RemoveAttribute("//z", null, "b"));
        assertEquals(
                new OperationReport("removeAttribute", "//y", 1, 0, java.util.Collections.singletonList(null)),
                r.reports().get(1));
    }

    @Test
    void charactersTheEncodingCannotRepresentBecomeReferences() throws Exception {
        assertGolden("iso-8859-1-unencodable", new SetText("/a", null, "prezzo 5€ è"));
    }

    @Test
    void csprojWithBomAndCrlf() throws Exception {
        assertGolden("csproj-bom-crlf", new SetText("//Version", null, "1.3.0"));
    }
}
