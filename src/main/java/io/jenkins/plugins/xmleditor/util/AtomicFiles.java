package io.jenkins.plugins.xmleditor.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Writes a file through a temporary file in the same directory, so readers never see a half-written file.
 *
 * <p>Unlike {@code hudson.util.AtomicFileWriter}, which is a character {@code Writer}, this writes the exact bytes
 * produced by the lossless serializer (encoding, BOM and character references are already applied), keeps the
 * permissions of the replaced file and writes through symbolic links instead of replacing them. Like it, the data is
 * flushed to the disk before the rename.
 */
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
        // java.io creates the file with the default permissions (umask), NIO's createTempFile with owner-only ones:
        // a new file must look like any other file created in the workspace. The prefix needs at least 3 characters.
        Path tmp = File.createTempFile("." + target.getFileName() + ".", ".tmp", dir.toFile())
                .toPath();
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            copyPermissions(target, tmp);
            move(tmp, target);
            syncDirectory(dir);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
            // A file system without atomic rename, or (on Windows) a target briefly locked by another process.
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Makes the rename durable. Directories cannot be opened on Windows, where this is not needed. */
    private static void syncDirectory(Path dir) {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // Best effort: the file has been written and renamed, some file systems do not support this.
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
