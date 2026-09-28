package io.jenkins.plugins.xmleditor.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Writes a file through a temporary file in the same directory, so readers never see a half-written file. */
public final class AtomicFiles {

    private AtomicFiles() {}

    /** Writes {@code content}; when {@code file} is a symbolic link the file it points to is replaced and the link kept. */
    public static void write(Path file, byte[] content) throws IOException {
        Path target = Files.isSymbolicLink(file) ? file.toRealPath() : file;
        Path dir = target.toAbsolutePath().getParent();
        if (dir == null) {
            throw new IOException("Cannot write " + target + ": it has no parent directory");
        }
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName(), ".tmp");
        try {
            Files.write(tmp, content);
            copyPermissions(target, tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void copyPermissions(Path from, Path to) throws IOException {
        if (!Files.exists(from)) {
            return;
        }
        PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class);
        PosixFileAttributeView dest = Files.getFileAttributeView(to, PosixFileAttributeView.class);
        if (source != null && dest != null) {
            Set<PosixFilePermission> permissions = source.readAttributes().permissions();
            dest.setPermissions(permissions);
        }
    }
}
