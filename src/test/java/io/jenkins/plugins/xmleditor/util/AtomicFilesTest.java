package io.jenkins.plugins.xmleditor.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    @TempDir
    Path dir;

    @Test
    void replacesContentAndLeavesNoTemporaryFiles() throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<old/>");
        AtomicFiles.write(file, "<new/>".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("<new/>".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void writingThroughASymbolicLinkKeepsTheLink() throws Exception {
        Path real = dir.resolve("real.xml");
        Files.writeString(real, "<old/>");
        Path link = dir.resolve("link.xml");
        try {
            Files.createSymbolicLink(link, real);
        } catch (java.io.IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links not available: " + e);
        }
        AtomicFiles.write(link, "<new/>".getBytes(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isSymbolicLink(link));
        assertEquals("<new/>", Files.readString(real));
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path file = dir.resolve("out").resolve("b.xml");
        AtomicFiles.write(file, new byte[] {'<', 'b', '/', '>'});
        assertEquals("<b/>", Files.readString(file));
    }
}
