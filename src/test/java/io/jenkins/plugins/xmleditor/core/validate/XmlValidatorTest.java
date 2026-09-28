package io.jenkins.plugins.xmleditor.core.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlValidatorTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="config">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="port" type="xs:int"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path ws;

    private Path write(String name, String content) throws Exception {
        Path p = ws.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }

    @Test
    void wellFormedWithoutSchemaIsValid() throws Exception {
        ValidationResult r = XmlValidator.validate("<a><b/></a>", null, ws);
        assertTrue(r.valid());
        assertTrue(r.issues().isEmpty());
    }

    @Test
    void malformedIsInvalidWithPosition() throws Exception {
        ValidationResult r = XmlValidator.validate("<a>\n<b>\n</a>", null, ws);
        assertFalse(r.valid());
        assertEquals(3, r.issues().get(0).line());
        assertEquals("FATAL", r.issues().get(0).severity());
    }

    @Test
    void schemaViolationsAreAllReported() throws Exception {
        Path xsd = write("config.xsd", XSD);
        assertTrue(XmlValidator.validate("<config><port>8080</port></config>", xsd, ws)
                .valid());
        ValidationResult r = XmlValidator.validate("<config>\n  <port>abc</port>\n  <extra/>\n</config>", xsd, ws);
        assertFalse(r.valid());
        assertTrue(r.issues().size() >= 2, r.issues().toString());
        assertEquals(2, r.issues().get(0).line());
        assertEquals("ERROR", r.issues().get(0).severity());
    }

    @Test
    void includesInsideTheWorkspaceAreResolved() throws Exception {
        write("schema/types.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="portType"><xs:restriction base="xs:int"/></xs:simpleType>
                </xs:schema>
                """);
        Path main = write("schema/main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="types.xsd"/>
                  <xs:element name="port" type="portType"/>
                </xs:schema>
                """);
        assertTrue(XmlValidator.validate("<port>1</port>", main, ws).valid());
        assertFalse(XmlValidator.validate("<port>x</port>", main, ws).valid());
    }

    @Test
    void includesOutsideTheWorkspaceAreRefused() throws Exception {
        Path outside = Files.createTempDirectory("outside");
        Files.writeString(outside.resolve("types.xsd"), "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="%s"/>
                </xs:schema>
                """.formatted(outside.resolve("types.xsd").toUri()));
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> XmlValidator.validate("<a/>", main, ws));
        assertTrue(e.getMessage().contains("outside"), e.getMessage());
    }

    @Test
    void remoteImportsAreRefusedWithoutNetworkAccess() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:import namespace="urn:x" schemaLocation="http://10.255.255.1:81/x.xsd"/>
                </xs:schema>
                """);
        XmlEditorException e = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThrows(XmlEditorException.class, () -> XmlValidator.validate("<a/>", main, ws)));
        assertTrue(e.getMessage().contains("http"), e.getMessage());
    }

    @Test
    void schemaThatIsNotXmlIsAClearError() throws Exception {
        Path bad = write("bad.xsd", "not a schema");
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> XmlValidator.validate("<a/>", bad, ws));
        assertTrue(e.getMessage().contains("bad.xsd"), e.getMessage());
    }
}
