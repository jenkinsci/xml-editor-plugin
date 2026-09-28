package io.jenkins.plugins.xmleditor.core.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XmlToMapConverterTest {

    private static Object convert(String xml) throws XmlEditorException {
        LElement root = XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "t.xml", 100_000)
                .root();
        return XmlToMapConverter.convert(root);
    }

    @Test
    void textOnlyElementBecomesATrimmedString() throws Exception {
        assertEquals("1.0", convert("<version>\n  1.0\n</version>"));
        assertEquals("a & b", convert("<v>a &amp; b</v>"));
        assertEquals("", convert("<v/>"));
        assertEquals("", convert("<v>  </v>"));
    }

    @Test
    void attributesAndChildrenBecomeAMap() throws Exception {
        Object result = convert("<a id=\"x\"><b>1</b><c>2</c></a>");
        assertEquals(Map.of("@id", "x", "b", "1", "c", "2"), result);
        assertInstanceOf(LinkedHashMap.class, result);
    }

    @Test
    void repeatedChildrenBecomeAList() throws Exception {
        Object result = convert("<deps><d>1</d><other/><d>2</d><d><x>3</x></d></deps>");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals(List.of("1", "2", Map.of("x", "3")), map.get("d"));
        assertInstanceOf(ArrayList.class, map.get("d"));
        assertEquals("", map.get("other"));
    }

    @Test
    void mixedContentKeepsTheTextUnderHashText() throws Exception {
        assertEquals(Map.of("#text", "hello  end", "b", "w"), convert("<p>hello <b>w</b> end</p>"));
        assertEquals(Map.of("@x", "1", "#text", "t"), convert("<a x=\"1\">t</a>"));
    }

    @Test
    void commentsProcessingInstructionsAndNamespaceDeclarationsAreIgnored() throws Exception {
        assertEquals(
                Map.of("p:b", "1", "@p:attr", "v"),
                convert("<p:a xmlns:p=\"u\" p:attr=\"v\"><!-- c --><?pi x?><p:b>1</p:b></p:a>"));
    }

    @Test
    void cdataIsText() throws Exception {
        assertEquals("<x>", convert("<a><![CDATA[<x>]]></a>"));
    }

    @Test
    void keyOrderFollowsTheDocument() throws Exception {
        Map<?, ?> map = assertInstanceOf(Map.class, convert("<a z=\"1\"><y/><x/><y/></a>"));
        assertEquals(List.of("@z", "y", "x"), new ArrayList<>(map.keySet()));
    }
}
