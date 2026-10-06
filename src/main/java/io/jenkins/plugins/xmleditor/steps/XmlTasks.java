package io.jenkins.plugins.xmleditor.steps;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/** Runs a read-only {@link XmlTask} ({@code xmlQuery}, {@code xmlRead}) on the document selected by a step. */
final class XmlTasks {

    private XmlTasks() {}

    /**
     * Runs {@code task} on the document: on the agent holding {@code file}, or on the controller for {@code text}.
     * Messages of the task are printed to the build log.
     */
    static Serializable run(StepContext context, AbstractXmlStep step, XmlTask task) throws Exception {
        String file = step.getFile();
        String text = step.getText();
        if ((file == null) == (text == null)) {
            throw new AbortException("Specify exactly one of 'file' or 'text'");
        }
        long maxBytes = step.maxBytes();
        XmlTaskResult result;
        if (text != null) {
            try {
                result = task.run(XmlDocuments.parseText(text, "text", maxBytes));
            } catch (XmlEditorException e) {
                throw new AbortException(e.getMessage());
            }
        } else {
            FilePath cwd = context.get(FilePath.class);
            if (cwd == null) {
                throw new AbortException("'file' requires a workspace: call the step inside node { ... }");
            }
            result = cwd.act(new ReadXmlCallable(file, maxBytes, task));
        }
        TaskListener listener = context.get(TaskListener.class);
        for (String message : result.messages()) {
            listener.getLogger().println(message);
        }
        return result.value();
    }
}
