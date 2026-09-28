package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.diff.LineDiff;
import io.jenkins.plugins.xmleditor.core.edit.EditResult;
import io.jenkins.plugins.xmleditor.core.edit.XmlEditor;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.util.AtomicFiles;
import io.jenkins.plugins.xmleditor.util.WorkspacePaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.MasterToSlaveFileCallable;

/**
 * Edits one or more workspace files on the agent that holds them. All files are parsed and edited in memory first;
 * nothing is written unless every operation succeeded on every file.
 */
public final class EditXmlCallable extends MasterToSlaveFileCallable<ArrayList<EditSummary>> {

    private static final long serialVersionUID = 1L;
    static final int DIFF_CONTEXT = 3;
    static final int DIFF_MAX_LINES = 200;

    private final EditRequest request;

    public EditXmlCallable(EditRequest request) {
        this.request = request;
    }

    private record Pending(String name, Path path, LDocument doc, EditResult result) {}

    @Override
    public ArrayList<EditSummary> invoke(File dir, VirtualChannel channel) throws IOException {
        Path root = dir.toPath();
        boolean multi = request.files() != null;
        List<String> names = multi ? scan(dir) : List.of(request.file());
        List<Pending> pending = new ArrayList<>();
        try {
            for (String name : names) {
                Path path = WorkspacePaths.resolveInside(root, name);
                LDocument doc = XmlDocuments.parse(
                        ReadXmlCallable.readFile(path, name, request.maxBytes()), name, request.maxBytes());
                EditResult result;
                try {
                    result = XmlEditor.apply(doc, request.operations(), request.options());
                } catch (XmlEditorException e) {
                    throw new XmlEditorException(multi ? name + ": " + e.getMessage() : e.getMessage(), e);
                }
                pending.add(new Pending(name, path, doc, result));
            }
            Path output =
                    request.outputFile() == null ? null : WorkspacePaths.resolveInside(root, request.outputFile());
            ArrayList<EditSummary> summaries = new ArrayList<>();
            for (Pending p : pending) {
                String written = null;
                if (!request.dryRun() && (p.result().changed() || output != null)) {
                    AtomicFiles.write(output == null ? p.path() : output, XmlDocuments.toBytes(p.doc()));
                    written = output == null ? p.name() : request.outputFile();
                }
                String diff = request.showDiff()
                        ? LineDiff.unified(
                                p.result().before(), p.result().after(), p.name(), DIFF_CONTEXT, DIFF_MAX_LINES)
                        : "";
                summaries.add(new EditSummary(
                        p.name(),
                        p.result().changed(),
                        written,
                        p.result().reports(),
                        diff,
                        p.result().warnings(),
                        request.dryRun()));
            }
            return summaries;
        } catch (XmlEditorException e) {
            throw new AbortException(e.getMessage());
        }
    }

    private List<String> scan(File dir) throws AbortException {
        String[] found = dir.isDirectory()
                ? Util.createFileSet(dir, request.files(), request.excludes())
                        .getDirectoryScanner()
                        .getIncludedFiles()
                : new String[0];
        if (found.length == 0) {
            throw new AbortException("No files match '" + request.files() + "'"
                    + (request.excludes() == null ? "" : " (excludes '" + request.excludes() + "')") + " in " + dir);
        }
        return Arrays.stream(found).map(f -> f.replace('\\', '/')).sorted().toList();
    }
}
