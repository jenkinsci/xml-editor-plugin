package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.io.XmlSource;
import io.jenkins.plugins.xmleditor.core.validate.ValidationIssue;
import io.jenkins.plugins.xmleditor.core.validate.ValidationResult;
import io.jenkins.plugins.xmleditor.core.validate.XmlValidator;
import io.jenkins.plugins.xmleditor.util.WorkspacePaths;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import jenkins.MasterToSlaveFileCallable;

/** Validates a workspace file (or text) against an optional XSD from the workspace, on the agent. */
public final class ValidateCallable extends MasterToSlaveFileCallable<ValidationResult> {

    private static final long serialVersionUID = 1L;
    static final int MAX_REPORTED = 20;

    private final String file;
    private final String text;
    private final String schema;
    private final long maxBytes;

    public ValidateCallable(String file, String text, String schema, long maxBytes) {
        this.file = file;
        this.text = text;
        this.schema = schema;
        this.maxBytes = maxBytes;
    }

    @Override
    public ValidationResult invoke(File dir, VirtualChannel channel) throws IOException {
        return validate(dir.toPath());
    }

    /** @param dir workspace directory; may be {@code null} only for text without schema */
    ValidationResult validate(Path dir) throws IOException {
        try {
            String xml;
            if (file != null) {
                byte[] bytes = ReadXmlCallable.readFile(WorkspacePaths.resolveInside(dir, file), file, maxBytes);
                try {
                    xml = XmlSource.decode(bytes).text();
                } catch (XmlEditorException e) {
                    return new ValidationResult(false, List.of(new ValidationIssue(0, 0, "FATAL", e.getMessage())));
                }
            } else {
                xml = text;
            }
            Path schemaPath = null;
            if (schema != null) {
                schemaPath = WorkspacePaths.resolveInside(dir, schema);
                if (!Files.isRegularFile(schemaPath)) {
                    throw new XmlEditorException("Schema not found: " + schema);
                }
            }
            return XmlValidator.validate(xml, schemaPath, dir);
        } catch (XmlEditorException e) {
            throw new AbortException(e.getMessage());
        }
    }

    /** Prints the outcome and, when requested, fails on errors. */
    public static void report(ValidationResult result, String name, PrintStream logger, boolean failOnError)
            throws AbortException {
        if (result.valid() && result.issues().isEmpty()) {
            logger.println("xmlValidate: " + name + " is valid");
            return;
        }
        String summary = describe(result, name);
        if (!result.valid() && failOnError) {
            throw new AbortException(summary);
        }
        logger.println(summary);
    }

    static String describe(ValidationResult result, String name) {
        StringBuilder sb = new StringBuilder("xmlValidate: " + name)
                .append(result.valid() ? " is valid, with warnings:" : " is not valid:");
        List<ValidationIssue> issues = result.issues();
        for (int i = 0; i < issues.size() && i < MAX_REPORTED; i++) {
            ValidationIssue issue = issues.get(i);
            sb.append("\n  ").append(issue.severity()).append(' ').append(issue);
        }
        if (issues.size() > MAX_REPORTED) {
            sb.append("\n  ... and ").append(issues.size() - MAX_REPORTED).append(" more");
        }
        return sb.toString();
    }

    static LinkedHashMap<String, Object> toPipelineValue(ValidationResult result) {
        ArrayList<LinkedHashMap<String, Object>> errors = new ArrayList<>();
        for (ValidationIssue issue : result.issues()) {
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("line", issue.line());
            e.put("column", issue.column());
            e.put("severity", issue.severity());
            e.put("message", issue.message());
            errors.add(e);
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("valid", result.valid());
        value.put("errors", errors);
        return value;
    }
}
