package io.jenkins.plugins.xmleditor.core.parse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Invariant R1: parsing and serializing without edits must give back the very same bytes. */
class RoundTripCorpusTest {

    static List<Path> corpus() throws IOException, URISyntaxException {
        Path dir = Path.of(RoundTripCorpusTest.class.getResource("/corpus").toURI());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> list = files.sorted().toList();
            assertTrue(list.size() >= 10, "corpus unexpectedly small: " + list);
            return list;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void roundTripIsByteIdentical(Path file) throws Exception {
        byte[] original = Files.readAllBytes(file);
        XmlSource source = XmlSource.decode(original);
        LDocument doc = LosslessParser.parse(source);
        assertArrayEquals(
                original, source.encode(doc.serialize()), file.getFileName().toString());
    }

    @Test
    void largePomRoundTrip() throws Exception {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\"?>\r\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\r\n  <dependencies>\r\n");
        for (int i = 0; i < 20_000; i++) {
            sb.append("    <!-- dep ")
                    .append(i)
                    .append(" -->\r\n    <dependency>\r\n      <groupId>g")
                    .append(i)
                    .append("</groupId>\r\n      <artifactId attr='")
                    .append(i)
                    .append("'>a</artifactId>\r\n    </dependency>\r\n");
        }
        sb.append("  </dependencies>\r\n</project>\r\n");
        byte[] original = sb.toString().getBytes(StandardCharsets.UTF_8);
        XmlSource source = XmlSource.decode(original);
        assertArrayEquals(original, source.encode(LosslessParser.parse(source).serialize()));
    }
}
