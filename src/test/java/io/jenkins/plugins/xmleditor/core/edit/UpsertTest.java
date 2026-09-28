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

class UpsertTest {

    private static LDocument doc(String xml) throws XmlEditorException {
        return XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "t.xml", 100_000);
    }

    private static String failure(String xml, String xpath) throws XmlEditorException {
        LDocument d = doc(xml);
        return assertThrows(
                        XmlEditorException.class,
                        () -> XmlEditor.apply(d, List.of(new Upsert(xpath, null, "1")), XPathOptions.defaults()))
                .getMessage();
    }

    @Test
    void existingNodesAreUpdatedLikeSetText() throws Exception {
        LDocument d = doc("<r><v>1</v><v>2</v></r>");
        EditResult r = XmlEditor.apply(d, List.of(new Upsert("/r/v", null, "3")), XPathOptions.defaults());
        assertEquals("<r><v>3</v><v>3</v></r>", d.serialize());
        assertEquals(
                new OperationReport("upsert", "/r/v", 2, 2, List.of("1", "2")),
                r.reports().get(0));
    }

    @Test
    void missingElementIsCreatedInACrlfFileWithBom() throws Exception {
        EditResult r =
                assertGolden("upsert-csproj-crlf-bom", new Upsert("/Project/PropertyGroup/Version", null, "1.2.3"));
        assertEquals(
                new OperationReport("upsert", "/Project/PropertyGroup/Version", 0, 1, List.of()),
                r.reports().get(0));
    }

    @Test
    void multiLineValuesAreNotReindented() throws Exception {
        LDocument d = doc("<r>\n  <a/>\n</r>");
        XmlEditor.apply(d, List.of(new Upsert("/r/v", null, "line1\nline2")), XPathOptions.defaults());
        assertEquals(
                java.util.Optional.of("line1\nline2"),
                new io.jenkins.plugins.xmleditor.core.xpath.XPathEngine(d, XPathOptions.defaults())
                        .evaluateString("/r/v"));
    }

    @Test
    void severalMissingLevelsAreCreatedAndTheValueEscaped() throws Exception {
        assertGolden("upsert-two-levels", new Upsert("/r/b/c", null, "x & y"));
    }

    @Test
    void finalAttributeStepCreatesOrUpdatesAttributes() throws Exception {
        assertGolden("upsert-attributes", new Upsert("/r/a/@k", null, "v"), new Upsert("/r/n/@k", null, "w"));
    }

    @Test
    void attributePredicatesBecomeAttributesOfCreatedElements() throws Exception {
        assertGolden("upsert-predicate", new Upsert("/Project/ItemGroup[@Label='Deps']/X", null, "1"));
    }

    @Test
    void createdElementsInheritTheDefaultNamespace() throws Exception {
        assertGolden("upsert-pom-namespace", new Upsert("/project/properties/revision", null, "2.0"));
    }

    @Test
    void ambiguousParentIsAnError() throws Exception {
        String message = failure("<r><a/><a/></r>", "/r/a/b");
        assertTrue(message.contains("/r/a") && message.contains("2"), message);
    }

    @Test
    void unsupportedPathsAreExplained() throws Exception {
        assertTrue(failure("<r/>", "//x").contains("absolute path"), failure("<r/>", "//x"));
        assertTrue(failure("<r/>", "/r/x[1]").contains("absolute path"));
        assertTrue(failure("<r/>", "/r/x/text()").contains("absolute path"));
        assertTrue(failure("<r/>", "/r/@a/b").contains("absolute path"));
    }

    @Test
    void theRootElementIsNeverCreated() throws Exception {
        String message = failure("<r/>", "/other/x");
        assertTrue(message.contains("root"), message);
    }

    @Test
    void explicitExpectationStillApplies() throws Exception {
        LDocument d = doc("<r/>");
        assertThrows(
                XmlEditorException.class,
                () -> XmlEditor.apply(d, List.of(new Upsert("/r/v", "ONE", "1")), XPathOptions.defaults()));
    }
}
