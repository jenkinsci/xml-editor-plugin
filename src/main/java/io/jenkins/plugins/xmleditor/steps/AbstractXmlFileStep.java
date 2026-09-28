package io.jenkins.plugins.xmleditor.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundSetter;

/** Steps that read an XML document from a workspace file or from text. */
public abstract class AbstractXmlFileStep extends AbstractXmlStep {

    private String file;
    private String text;

    @CheckForNull
    public String getFile() {
        return file;
    }

    @DataBoundSetter
    public void setFile(String file) {
        this.file = file == null || file.isEmpty() ? null : file;
    }

    @CheckForNull
    public String getText() {
        return text;
    }

    @DataBoundSetter
    public void setText(String text) {
        this.text = text == null || text.isEmpty() ? null : text;
    }

    /**
     * Runs {@code task} on the document: on the agent holding {@code file}, or on the controller for {@code text}.
     * Messages of the task are printed to the build log.
     */
    Serializable runTask(StepContext context, XmlTask task) throws Exception {
        if ((file == null) == (text == null)) {
            throw new AbortException("Specify exactly one of 'file' or 'text'");
        }
        long maxBytes = maxBytes();
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
