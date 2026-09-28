package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.util.WorkspacePaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jenkins.MasterToSlaveFileCallable;

/** Reads, parses and processes a workspace file where it lives (on the agent). */
final class ReadXmlCallable extends MasterToSlaveFileCallable<XmlTaskResult> {

    private static final long serialVersionUID = 1L;

    private final String file;
    private final long maxBytes;
    private final XmlTask task;

    ReadXmlCallable(String file, long maxBytes, XmlTask task) {
        this.file = file;
        this.maxBytes = maxBytes;
        this.task = task;
    }

    @Override
    public XmlTaskResult invoke(File dir, VirtualChannel channel) throws IOException {
        try {
            Path path = WorkspacePaths.resolveInside(dir.toPath(), file);
            return task.run(XmlDocuments.parse(readFile(path, file, maxBytes), file, maxBytes));
        } catch (XmlEditorException e) {
            throw new AbortException(e.getMessage());
        }
    }

    static byte[] readFile(Path path, String displayName, long maxBytes) throws IOException, XmlEditorException {
        if (!Files.isRegularFile(path)) {
            throw new XmlEditorException("File not found: " + displayName);
        }
        if (Files.size(path) > maxBytes) {
            throw new XmlEditorException(displayName + " is too large (" + Files.size(path) + " bytes, limit "
                    + maxBytes + "); raise maxSizeMb if this is expected");
        }
        return Files.readAllBytes(path);
    }
}
