package io.jenkins.plugins.xmleditor.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
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
    void keepsThePermissionsOfTheReplacedFile() throws Exception {
        assumePosix();
        Path file = dir.resolve("run.sh");
        Files.writeString(file, "old");
        Set<PosixFilePermission> executable = PosixFilePermissions.fromString("rwxr-x---");
        Files.setPosixFilePermissions(file, executable);
        AtomicFiles.write(file, "new".getBytes(StandardCharsets.UTF_8));
        assertEquals(executable, Files.getPosixFilePermissions(file));
    }

    @Test
    void newFilesGetTheDefaultPermissionsNotOwnerOnly() throws Exception {
        assumePosix();
        Path reference = Files.createFile(dir.resolve("reference.xml"));
        Path file = dir.resolve("new.xml");
        AtomicFiles.write(file, "<new/>".getBytes(StandardCharsets.UTF_8));
        assertEquals(Files.getPosixFilePermissions(reference), Files.getPosixFilePermissions(file));
    }

    private void assumePosix() {
        Assumptions.assumeTrue(
                Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null, "POSIX permissions only");
    }

    @Test
    void writesFilesWithOneCharacterNames() throws Exception {
        Path file = dir.resolve("a");
        AtomicFiles.write(file, "<a/>".getBytes(StandardCharsets.UTF_8));
        assertEquals("<a/>", Files.readString(file));
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path file = dir.resolve("out").resolve("b.xml");
        AtomicFiles.write(file, new byte[] {'<', 'b', '/', '>'});
        assertEquals("<b/>", Files.readString(file));
    }
}
