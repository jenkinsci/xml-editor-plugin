package io.jenkins.plugins.xmleditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspacePathsTest {

    @TempDir
    Path root;

    @Test
    void relativePathInsideIsResolved() throws Exception {
        assertEquals(root.resolve("sub").resolve("a.xml"), WorkspacePaths.resolveInside(root, "sub/a.xml"));
        assertEquals(root.resolve("a.xml"), WorkspacePaths.resolveInside(root, "sub/../a.xml"));
        assertEquals(root.resolve("sub").resolve("a.xml"), WorkspacePaths.resolveInside(root, "sub\\a.xml"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../x.xml",
                "a/../../x.xml",
                "/etc/passwd",
                "\\\\server\\share\\x.xml",
                "C:\\x.xml",
                "c:/x.xml",
                "",
                "  "
            })
    void pathsOutsideTheWorkspaceAreRejected(String path) {
        assertThrows(XmlEditorException.class, () -> WorkspacePaths.resolveInside(root, path));
    }

    @Test
    void symlinkEscapingTheWorkspaceIsRejected() throws Exception {
        Path outside = Files.createTempDirectory("outside");
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not available: " + e);
        }
        assertThrows(XmlEditorException.class, () -> WorkspacePaths.resolveInside(root, "link/x.xml"));
    }
}
