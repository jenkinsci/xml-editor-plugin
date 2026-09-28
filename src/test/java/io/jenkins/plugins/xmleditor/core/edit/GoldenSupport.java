package io.jenkins.plugins.xmleditor.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Applies operations to {@code golden/<name>.input.xml} and compares the bytes with {@code <name>.expected.xml}. */
final class GoldenSupport {

    private GoldenSupport() {}

    static byte[] resource(String path) throws IOException {
        try (InputStream in = GoldenSupport.class.getResourceAsStream("/golden/" + path)) {
            if (in == null) {
                throw new IOException("missing test resource golden/" + path);
            }
            return in.readAllBytes();
        }
    }

    static EditResult assertGolden(String name, XmlOperation... ops) throws Exception {
        LDocument doc = XmlDocuments.parse(resource(name + ".input.xml"), name, 10_000_000);
        EditResult result = XmlEditor.apply(doc, List.of(ops), XPathOptions.defaults());
        byte[] actual = XmlDocuments.toBytes(doc);
        byte[] expected = resource(name + ".expected.xml");
        // compare as visible strings first for a readable diff, then byte for byte
        assertEquals(visible(expected), visible(actual), name);
        assertEquals(
                new String(expected, StandardCharsets.ISO_8859_1), new String(actual, StandardCharsets.ISO_8859_1));
        return result;
    }

    private static String visible(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1)
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
