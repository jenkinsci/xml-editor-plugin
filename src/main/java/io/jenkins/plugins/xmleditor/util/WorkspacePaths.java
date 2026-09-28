package io.jenkins.plugins.xmleditor.util;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Resolves user supplied paths and makes sure they stay inside the workspace (also through symbolic links). */
public final class WorkspacePaths {

    private WorkspacePaths() {}

    public static Path resolveInside(Path root, String relative) throws XmlEditorException {
        if (relative == null || relative.isBlank()) {
            throw new XmlEditorException("The file path must not be empty");
        }
        String normalized = relative.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new XmlEditorException(
                    "Absolute paths are not allowed: '" + relative + "'. Use a path relative to the workspace");
        }
        Path base = root.toAbsolutePath().normalize();
        Path resolved;
        try {
            resolved = base.resolve(normalized).normalize();
        } catch (InvalidPathException e) {
            throw new XmlEditorException("Invalid path '" + relative + "': " + e.getMessage(), e);
        }
        if (!resolved.startsWith(base) || resolved.equals(base)) {
            throw outside(relative);
        }
        Path existing = resolved;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing != null) {
            try {
                if (!existing.toRealPath().startsWith(base.toRealPath())) {
                    throw outside(relative);
                }
            } catch (IOException e) {
                throw new XmlEditorException("Cannot resolve path '" + relative + "': " + e.getMessage(), e);
            }
        }
        return resolved;
    }

    private static XmlEditorException outside(String relative) {
        return new XmlEditorException("The path '" + relative + "' points outside the workspace");
    }
}
