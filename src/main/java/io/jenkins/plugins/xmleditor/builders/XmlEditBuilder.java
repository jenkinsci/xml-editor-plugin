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
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import io.jenkins.plugins.xmleditor.ops.SourceFormValidation;
import io.jenkins.plugins.xmleditor.ops.XmlOperationDescribable;
import io.jenkins.plugins.xmleditor.steps.EditRequest;
import io.jenkins.plugins.xmleditor.steps.EditSummary;
import io.jenkins.plugins.xmleditor.steps.EditXmlCallable;
import io.jenkins.plugins.xmleditor.util.NamespaceLines;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/** Freestyle build step "Edit XML file": same operations as {@code xmlEdit}, with {@code $VARIABLE} expansion. */
public class XmlEditBuilder extends Builder implements SimpleBuildStep {

    static final int DEFAULT_MAX_SIZE_MB = 50;

    private static final String FILE_OR_FILES = "'file' or 'files'";

    private final String file;
    private final List<XmlOperationDescribable> operations;
    private String files;
    private String excludes;
    private String outputFile;
    private boolean showDiff = true;
    private boolean dryRun;
    private boolean strictNamespaces;
    private String namespaces;
    private int maxSizeMb = DEFAULT_MAX_SIZE_MB;

    @DataBoundConstructor
    public XmlEditBuilder(String file, List<XmlOperationDescribable> operations) {
        this.file = Util.fixEmptyAndTrim(file);
        this.operations = operations == null ? new ArrayList<>() : new ArrayList<>(operations);
    }

    @CheckForNull
    public String getFile() {
        return file;
    }

    public List<XmlOperationDescribable> getOperations() {
        return operations;
    }

    /** Ant pattern(s), comma separated; used instead of {@code file}. */
    @CheckForNull
    public String getFiles() {
        return files;
    }

    @DataBoundSetter
    public void setFiles(String files) {
        this.files = Util.fixEmptyAndTrim(files);
    }

    @CheckForNull
    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter
    public void setExcludes(String excludes) {
        this.excludes = Util.fixEmptyAndTrim(excludes);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    @DataBoundSetter
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @CheckForNull
    public String getOutputFile() {
        return outputFile;
    }

    @DataBoundSetter
    public void setOutputFile(String outputFile) {
        this.outputFile = Util.fixEmptyAndTrim(outputFile);
    }

    public boolean isShowDiff() {
        return showDiff;
    }

    @DataBoundSetter
    public void setShowDiff(boolean showDiff) {
        this.showDiff = showDiff;
    }

    /** Namespace prefixes for XPath, one {@code prefix=uri} per line. */
    @CheckForNull
    public String getNamespaces() {
        return namespaces;
    }

    @DataBoundSetter
    public void setNamespaces(String namespaces) {
        this.namespaces = Util.fixEmptyAndTrim(namespaces);
    }

    public boolean isStrictNamespaces() {
        return strictNamespaces;
    }

    @DataBoundSetter
    public void setStrictNamespaces(boolean strictNamespaces) {
        this.strictNamespaces = strictNamespaces;
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
        if (maxSizeMb <= 0) {
            throw new AbortException("Edit XML file: the maximum size must be greater than 0");
        }
        if ((file == null) == (files == null)) {
            throw new AbortException("Edit XML file: specify exactly one of 'file' or 'files'");
        }
        Map<String, String> prefixes;
        try {
            prefixes = NamespaceLines.parse(namespaces);
        } catch (XmlEditorException e) {
            throw new AbortException("Edit XML file: " + e.getMessage());
        }
        List<XmlOperation> ops = new ArrayList<>();
        for (XmlOperationDescribable op : operations) {
            ops.add(op.toCore(env::expand));
        }
        EditRequest request = new EditRequest(
                expand(env, file),
                expand(env, files),
                expand(env, excludes),
                expand(env, outputFile),
                maxSizeMb * 1024L * 1024L,
                ops,
                new XPathOptions(prefixes, strictNamespaces),
                showDiff,
                dryRun);
        request.validate();
        List<EditSummary> summaries = workspace.act(new EditXmlCallable(request));
        summaries.forEach(s -> s.log(listener.getLogger()));
    }

    private static String expand(EnvVars env, String value) {
        return value == null ? null : env.expand(value);
    }

    @Extension
    @Symbol("xmlEditBuilder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Edit XML file";
        }

        @POST
        public FormValidation doCheckFile(
                @AncestorInPath Item item, @QueryParameter String value, @QueryParameter String files) {
            return SourceFormValidation.onlyOne(item, FILE_OR_FILES, value, files);
        }

        @POST
        public FormValidation doCheckFiles(
                @AncestorInPath Item item, @QueryParameter String value, @QueryParameter String file) {
            return SourceFormValidation.onlyOne(item, FILE_OR_FILES, value, file);
        }
    }
}
