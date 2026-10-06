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
import io.jenkins.plugins.xmleditor.core.validate.ValidationResult;
import io.jenkins.plugins.xmleditor.ops.SourceFormValidation;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/** {@code xmlValidate}: checks well-formedness and, optionally, conformance to an XSD. */
public class XmlValidateStep extends AbstractXmlStep {

    private String schema;
    private boolean failOnError = true;

    @DataBoundConstructor
    public XmlValidateStep() {}

    @CheckForNull
    public String getSchema() {
        return schema;
    }

    @DataBoundSetter
    public void setSchema(String schema) {
        this.schema = Util.fixEmptyAndTrim(schema);
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        private final transient XmlValidateStep step;

        Execution(XmlValidateStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            String file = step.getFile();
            String text = step.getText();
            if ((file == null) == (text == null)) {
                throw new AbortException("Specify exactly one of 'file' or 'text'");
            }
            ValidateCallable callable = new ValidateCallable(file, text, step.schema, step.maxBytes());
            FilePath cwd = getContext().get(FilePath.class);
            ValidationResult result;
            if (cwd != null) {
                result = cwd.act(callable);
            } else if (file == null && step.schema == null) {
                result = callable.validate(null);
            } else {
                throw new AbortException("xmlValidate with 'file' or 'schema' requires a workspace: use node { ... }");
            }
            ValidateCallable.report(
                    result,
                    file != null ? file : "text",
                    getContext().get(TaskListener.class).getLogger(),
                    step.failOnError);
            return ValidateCallable.toPipelineValue(result);
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "xmlValidate";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Validate an XML file (well-formedness, XSD)";
        }

        @POST
        public FormValidation doCheckFile(
                @AncestorInPath Item item, @QueryParameter String value, @QueryParameter String text) {
            return SourceFormValidation.onlyOne(item, FILE_OR_TEXT, value, text);
        }

        @POST
        public FormValidation doCheckText(
                @AncestorInPath Item item, @QueryParameter String value, @QueryParameter String file) {
            return SourceFormValidation.onlyOne(item, FILE_OR_TEXT, value, file);
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
