package io.jenkins.plugins.xmleditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NamespaceLinesTest {

    @Test
    void parsesOnePrefixPerLine() throws Exception {
        Map<String, String> ns = NamespaceLines.parse(" m = http://maven.apache.org/POM/4.0.0 \n\n  x=urn:x\r\n");
        assertEquals(Map.of("m", "http://maven.apache.org/POM/4.0.0", "x", "urn:x"), ns);
        assertEquals(new ArrayList<>(ns.keySet()), java.util.List.of("m", "x"));
    }

    @Test
    void emptyOrNullGivesNoPrefixes() throws Exception {
        assertTrue(NamespaceLines.parse(null).isEmpty());
        assertTrue(NamespaceLines.parse("   ").isEmpty());
    }

    @Test
    void invalidLinesAreRejectedWithTheirNumber() {
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> NamespaceLines.parse("m=urn:m\nbroken"));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertThrows(XmlEditorException.class, () -> NamespaceLines.parse("=urn:x"));
        assertThrows(XmlEditorException.class, () -> NamespaceLines.parse("x="));
    }
}
