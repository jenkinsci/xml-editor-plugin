package io.jenkins.plugins.xmleditor.core.xpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LAttribute;
import io.jenkins.plugins.xmleditor.core.model.LComment;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.model.LText;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class XPathEngineTest {

    private static final String POM = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <version>1.0</version>\n"
            + "  <dependencies>\n"
            + "    <dependency><artifactId>a</artifactId></dependency>\n"
            + "    <!-- note -->\n"
            + "    <dependency><artifactId>b</artifactId></dependency>\n"
            + "  </dependencies>\n"
            + "</project>";

    private static final String CSPROJ = "<Project xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">"
            + "<ItemGroup><PackageReference Include=\"log4net\"/><PackageReference Include=\"Serilog\"/></ItemGroup>"
            + "</Project>";

    private static LDocument doc(String xml) throws XmlEditorException {
        return XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "t.xml", 1_000_000);
    }

    private static XPathEngine engine(String xml) throws XmlEditorException {
        return new XPathEngine(doc(xml), XPathOptions.defaults());
    }

    @Test
    void defaultNamespaceIsTransparent() throws Exception {
        assertEquals(Optional.of("1.0"), engine(POM).evaluateString("/project/version"));
    }

    @Test
    void strictNamespacesRequireAPrefix() throws Exception {
        LDocument d = doc(POM);
        XPathEngine strict = new XPathEngine(d, new XPathOptions(Map.of(), true));
        assertEquals(Optional.empty(), strict.evaluateString("/project/version"));
        XPathEngine prefixed =
                new XPathEngine(d, new XPathOptions(Map.of("m", "http://maven.apache.org/POM/4.0.0"), true));
        assertEquals(Optional.of("1.0"), prefixed.evaluateString("/m:project/m:version"));
    }

    @Test
    void bindingAPrefixToTheDefaultNamespaceSwitchesToStandardRules() throws Exception {
        XPathEngine e =
                new XPathEngine(doc(POM), new XPathOptions(Map.of("m", "http://maven.apache.org/POM/4.0.0"), false));
        assertEquals(Optional.of("1.0"), e.evaluateString("/m:project/m:version"));
    }

    @Test
    void attributesOfLegacyCsproj() throws Exception {
        assertEquals(List.of("log4net", "Serilog"), engine(CSPROJ).evaluateList("//PackageReference/@Include"));
    }

    @Test
    void prefixesDeclaredInTheDocumentWorkWithoutConfiguration() throws Exception {
        XPathEngine e = engine("<r xmlns:ns=\"urn:x\"><ns:item ns:attr=\"v\">x</ns:item></r>");
        assertEquals(Optional.of("x"), e.evaluateString("/r/ns:item"));
        assertEquals(Optional.of("v"), e.evaluateString("/r/ns:item/@ns:attr"));
    }

    @Test
    void conflictingPrefixDeclarationsProduceAWarning() throws Exception {
        XPathEngine e = engine("<r><a xmlns:p=\"urn:1\"><p:x>1</p:x></a><b xmlns:p=\"urn:2\"><p:x>2</p:x></b></r>");
        assertEquals(List.of("1"), e.evaluateList("//p:x"));
        assertEquals(1, e.warnings().size());
        assertTrue(e.warnings().get(0).contains("urn:2"), e.warnings().get(0));
    }

    @Test
    void numbersAndBooleans() throws Exception {
        XPathEngine e = engine(POM);
        assertEquals(2.0, e.evaluateNumber("count(//dependency)"));
        assertTrue(e.evaluateBoolean("boolean(//dependency[artifactId='b'])"));
        assertFalse(e.evaluateBoolean("boolean(//dependency[artifactId='zzz'])"));
    }

    @Test
    void stringOfNonNodeExpression() throws Exception {
        assertEquals(Optional.of("1.0-SNAPSHOT"), engine(POM).evaluateString("concat(/project/version, '-SNAPSHOT')"));
    }

    @Test
    void invalidExpressionMentionsTheExpression() {
        XmlEditorException ex =
                assertThrows(XmlEditorException.class, () -> engine(POM).selectNodes("//["));
        assertTrue(ex.getMessage().contains("//["), ex.getMessage());
    }

    @Test
    void unknownPrefixIsAClearError() {
        XmlEditorException ex =
                assertThrows(XmlEditorException.class, () -> engine(POM).selectNodes("//zz:x"));
        assertTrue(ex.getMessage().contains("//zz:x"), ex.getMessage());
    }

    @Test
    void selectNodesReturnsTheLosslessNodes() throws Exception {
        LDocument d = doc(POM);
        XPathEngine e = new XPathEngine(d, XPathOptions.defaults());
        List<LItem> versions = e.selectNodes("/project/version");
        assertSame(childElement(d.root(), "version"), versions.get(0));

        LItem text = e.selectNodes("/project/version/text()").get(0);
        assertEquals("1.0", assertInstanceOf(LText.class, text).value());

        assertInstanceOf(LComment.class, e.selectNodes("//comment()").get(0));

        LItem attr =
                engine(CSPROJ).selectNodes("//PackageReference[2]/@Include").get(0);
        LAttribute a = assertInstanceOf(LAttribute.class, attr);
        assertEquals("Serilog", a.value());
        assertEquals("PackageReference", a.owner().name());
    }

    @Test
    void entitiesAreSeenDecoded() throws Exception {
        XPathEngine e = engine("<!DOCTYPE r [<!ENTITY who \"world\">]><r><a>hello &who; &amp; &lt;3</a></r>");
        assertEquals(Optional.of("hello world & <3"), e.evaluateString("/r/a"));
    }

    @Test
    void xmlnsDeclarationsAreNotAttributes() throws Exception {
        assertEquals(0.0, engine(POM).evaluateNumber("count(/project/@*)"));
    }

    private static LElement childElement(LElement parent, String name) {
        return parent.children().stream()
                .filter(n -> n instanceof LElement e && e.name().equals(name))
                .map(LElement.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
