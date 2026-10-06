package io.jenkins.plugins.xmleditor.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.AbortException;
import hudson.Util;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

/** Options shared by all XML steps: the document ({@code file} or {@code text}) and the size limit. */
public abstract class AbstractXmlStep extends Step {

    static final int DEFAULT_MAX_SIZE_MB = 50;

    /** The alternative ways to give the document, for messages of the steps that accept {@code file} or {@code text}. */
    static final String FILE_OR_TEXT = "'file' or 'text'";

    private String file;
    private String text;
    private int maxSizeMb = DEFAULT_MAX_SIZE_MB;

    @CheckForNull
    public String getFile() {
        return file;
    }

    @DataBoundSetter
    public void setFile(String file) {
        this.file = Util.fixEmptyAndTrim(file);
    }

    @CheckForNull
    public String getText() {
        return text;
    }

    /** The XML text is kept exactly as given: trimming it would change the document. */
    @DataBoundSetter
    public void setText(String text) {
        this.text = Util.fixEmpty(text);
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    @DataBoundSetter
    public void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    long maxBytes() throws AbortException {
        if (maxSizeMb <= 0) {
            throw new AbortException("maxSizeMb must be greater than 0");
        }
        return maxSizeMb * 1024L * 1024L;
    }
}
