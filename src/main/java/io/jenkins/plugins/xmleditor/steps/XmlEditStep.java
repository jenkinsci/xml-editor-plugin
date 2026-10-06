package io.jenkins.plugins.xmleditor.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.diff.LineDiff;
import io.jenkins.plugins.xmleditor.core.edit.EditResult;
import io.jenkins.plugins.xmleditor.core.edit.XmlEditor;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.parse.XmlDocuments;
import io.jenkins.plugins.xmleditor.ops.SourceFormValidation;
import io.jenkins.plugins.xmleditor.ops.XmlOperationDescribable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * {@code xmlEdit}: applies a list of operations to a workspace file, to all files matching a pattern, or to XML text,
 * preserving everything that is not changed.
 */
public class XmlEditStep extends AbstractXPathStep {

    private static final String FILE_FILES_OR_TEXT = "'file', 'files' or 'text'";

    private final List<XmlOperationDescribable> operations;
    private String files;
    private String excludes;
    private String outputFile;
    private boolean showDiff = true;
    private boolean dryRun;

    @DataBoundConstructor
    public XmlEditStep(List<XmlOperationDescribable> operations) {
        this.operations = operations == null ? List.of() : new ArrayList<>(operations);
    }

    public List<XmlOperationDescribable> getOperations() {
        return operations;
    }

    /** Ant pattern(s), comma separated, e.g. {@code **}{@code /*.csproj}. */
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

    public boolean isDryRun() {
        return dryRun;
    }

    @DataBoundSetter
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        private final transient XmlEditStep step;

        Execution(XmlEditStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            List<XmlOperation> ops = new ArrayList<>();
            for (XmlOperationDescribable op : step.operations) {
                ops.add(op.toCore(UnaryOperator.identity()));
            }
            PrintStream logger = getContext().get(TaskListener.class).getLogger();
            String text = step.getText();
            if (text != null) {
                return editText(text, ops, logger);
            }
            EditRequest request = new EditRequest(
                    step.getFile(),
                    step.files,
                    step.excludes,
                    step.outputFile,
                    step.maxBytes(),
                    ops,
                    step.xpathOptions(),
                    step.showDiff,
                    step.dryRun);
            request.validate();
            FilePath cwd = getContext().get(FilePath.class);
            if (cwd == null) {
                throw new AbortException("xmlEdit with 'file' or 'files' requires a workspace: use node { ... }");
            }
            List<EditSummary> summaries = cwd.act(new EditXmlCallable(request));
            summaries.forEach(s -> s.log(logger));
            if (step.files != null) {
                long changed = summaries.stream().filter(EditSummary::changed).count();
                logger.println("xmlEdit: " + summaries.size() + " file(s) matched, " + changed + " changed");
                return EditSummary.toPipelineValue(summaries, step.dryRun);
            }
            return summaries.get(0).toPipelineValue();
        }

        private Object editText(String text, List<XmlOperation> ops, PrintStream logger) throws Exception {
            if (step.getFile() != null || step.files != null) {
                throw new AbortException("Specify exactly one of 'file', 'files' or 'text'");
            }
            if (step.dryRun || step.outputFile != null || step.excludes != null) {
                throw new AbortException("'dryRun', 'outputFile' and 'excludes' cannot be used with 'text'");
            }
            if (ops.isEmpty()) {
                throw new AbortException("xmlEdit needs at least one operation");
            }
            try {
                LDocument doc = XmlDocuments.parseText(text, "text", step.maxBytes());
                EditResult result = XmlEditor.apply(doc, ops, step.xpathOptions());
                String diff = step.showDiff
                        ? LineDiff.unified(
                                result.before(),
                                result.after(),
                                "text",
                                EditXmlCallable.DIFF_CONTEXT,
                                EditXmlCallable.DIFF_MAX_LINES)
                        : "";
                EditSummary summary = new EditSummary(
                        "text", result.changed(), null, result.reports(), diff, result.warnings(), false);
                summary.log(logger);
                LinkedHashMap<String, Object> value = summary.toFileValue();
                value.remove("file");
                value.put("text", doc.serialize());
                return value;
            } catch (XmlEditorException e) {
                throw new AbortException(e.getMessage());
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "xmlEdit";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Edit XML files (lossless)";
        }

        @POST
        public FormValidation doCheckFile(
                @AncestorInPath Item item,
                @QueryParameter String value,
                @QueryParameter String files,
                @QueryParameter String text) {
            return SourceFormValidation.onlyOne(item, FILE_FILES_OR_TEXT, value, files, text);
        }

        @POST
        public FormValidation doCheckFiles(
                @AncestorInPath Item item,
                @QueryParameter String value,
                @QueryParameter String file,
                @QueryParameter String text) {
            return SourceFormValidation.onlyOne(item, FILE_FILES_OR_TEXT, value, file, text);
        }

        @POST
        public FormValidation doCheckText(
                @AncestorInPath Item item,
                @QueryParameter String value,
                @QueryParameter String file,
                @QueryParameter String files) {
            return SourceFormValidation.onlyOne(item, FILE_FILES_OR_TEXT, value, file, files);
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
