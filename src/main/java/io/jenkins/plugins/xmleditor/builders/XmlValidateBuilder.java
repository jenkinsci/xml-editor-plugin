package io.jenkins.plugins.xmleditor.builders;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import io.jenkins.plugins.xmleditor.core.validate.ValidationResult;
import io.jenkins.plugins.xmleditor.steps.ValidateCallable;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** Freestyle build step "Validate XML file": fails the build when the file is malformed or violates the XSD. */
public class XmlValidateBuilder extends Builder implements SimpleBuildStep {

    private final String file;
    private String schema;
    private int maxSizeMb = XmlEditBuilder.DEFAULT_MAX_SIZE_MB;

    @DataBoundConstructor
    public XmlValidateBuilder(String file) {
        this.file = Util.fixEmptyAndTrim(file);
    }

    @CheckForNull
    public String getFile() {
        return file;
    }

    @CheckForNull
    public String getSchema() {
        return schema;
    }

    @DataBoundSetter
    public void setSchema(String schema) {
        this.schema = Util.fixEmptyAndTrim(schema);
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    @DataBoundSetter
    public void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        if (file == null) {
            throw new AbortException("Validate XML file: the file is not configured");
        }
        if (maxSizeMb <= 0) {
            throw new AbortException("Validate XML file: the maximum size must be greater than 0");
        }
        String expandedFile = env.expand(file);
        ValidationResult result = workspace.act(new ValidateCallable(
                expandedFile, null, schema == null ? null : env.expand(schema), maxSizeMb * 1024L * 1024L));
        ValidateCallable.report(result, expandedFile, listener.getLogger(), true);
    }

    @Extension
    @Symbol("xmlValidateBuilder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Validate XML file";
        }
    }
}
