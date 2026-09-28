package io.jenkins.plugins.xmleditor.steps;

import io.jenkins.plugins.xmleditor.core.edit.OperationReport;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * What an edit did to one file (or to text), sent back from the agent.
 *
 * @param written path written, or {@code null} when nothing was written
 * @param dryRun {@code true} when nothing was written on purpose
 */
public record EditSummary(
        String file,
        boolean changed,
        String written,
        List<OperationReport> reports,
        String diff,
        List<String> messages,
        boolean dryRun)
        implements Serializable {

    public EditSummary {
        reports = List.copyOf(reports);
        messages = List.copyOf(messages);
    }

    public void log(PrintStream logger) {
        messages.forEach(logger::println);
        int i = 0;
        for (OperationReport r : reports) {
            logger.println("xmlEdit: " + file + ": operation " + ++i + " " + r.type() + " '" + r.xpath() + "': matched "
                    + r.matched() + ", modified " + r.modified());
        }
        if (!diff.isEmpty()) {
            logger.print(diff);
        }
        if (dryRun) {
            logger.println("xmlEdit: " + file + (changed ? " would be updated" : " unchanged") + " (dry run)");
        } else if (written != null) {
            logger.println(written.equals(file) ? "xmlEdit: " + file + " updated" : "xmlEdit: written " + written);
        } else {
            logger.println("xmlEdit: " + file + (changed ? " modified" : " unchanged"));
        }
    }

    /** {@code [file:, changed:, operations: [[type:, xpath:, matched:, modified:, oldValues:]]]} */
    public LinkedHashMap<String, Object> toFileValue() {
        ArrayList<LinkedHashMap<String, Object>> ops = new ArrayList<>();
        for (OperationReport r : reports) {
            LinkedHashMap<String, Object> op = new LinkedHashMap<>();
            op.put("type", r.type());
            op.put("xpath", r.xpath());
            op.put("matched", r.matched());
            op.put("modified", r.modified());
            op.put("oldValues", new ArrayList<>(r.oldValues()));
            ops.add(op);
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("file", file);
        value.put("changed", changed);
        value.put("operations", ops);
        return value;
    }

    /** Value returned by {@code xmlEdit file:}: the file value plus {@code dryRun}. */
    public LinkedHashMap<String, Object> toPipelineValue() {
        LinkedHashMap<String, Object> value = toFileValue();
        value.put("dryRun", dryRun);
        return value;
    }

    /** Value returned by {@code xmlEdit files:}: {@code [changed:, dryRun:, files: [...]]}. */
    public static LinkedHashMap<String, Object> toPipelineValue(List<EditSummary> summaries, boolean dryRun) {
        ArrayList<LinkedHashMap<String, Object>> files = new ArrayList<>();
        boolean changed = false;
        for (EditSummary s : summaries) {
            files.add(s.toFileValue());
            changed |= s.changed();
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("changed", changed);
        value.put("dryRun", dryRun);
        value.put("files", files);
        return value;
    }
}
