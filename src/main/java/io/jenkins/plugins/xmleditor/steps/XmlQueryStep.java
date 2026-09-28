package io.jenkins.plugins.xmleditor.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.xpath.XPathEngine;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import io.jenkins.plugins.xmleditor.ops.XPathFormValidation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
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

/** {@code xmlQuery}: evaluates an XPath expression and returns its value. */
public class XmlQueryStep extends AbstractXmlFileStep {

    enum ReturnType {
        STRING,
        LIST,
        NUMBER,
        BOOLEAN
    }

    private final String xpath;
    private String returnType = ReturnType.STRING.name();
    private boolean failIfNotFound = true;
    private String defaultValue;

    @DataBoundConstructor
    public XmlQueryStep(String xpath) {
        this.xpath = xpath;
    }

    public String getXpath() {
        return xpath;
    }

    public String getReturnType() {
        return returnType;
    }

    @DataBoundSetter
    public void setReturnType(String returnType) {
        this.returnType = returnType == null
                ? ReturnType.STRING.name()
                : returnType.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isFailIfNotFound() {
        return failIfNotFound;
    }

    @DataBoundSetter
    public void setFailIfNotFound(boolean failIfNotFound) {
        this.failIfNotFound = failIfNotFound;
    }

    /** Returned (with {@code STRING}) when nothing matches; takes precedence over {@code failIfNotFound}. */
    @CheckForNull
    public String getDefaultValue() {
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        private final transient XmlQueryStep step;

        Execution(XmlQueryStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            ReturnType type;
            try {
                type = ReturnType.valueOf(step.returnType);
            } catch (IllegalArgumentException e) {
                throw new AbortException(
                        "Invalid returnType '" + step.returnType + "': use STRING, LIST, NUMBER or BOOLEAN");
            }
            if (step.xpath == null || step.xpath.isBlank()) {
                throw new AbortException("xpath must not be empty");
            }
            String source = step.getFile() != null ? step.getFile() : "text";
            return step.runTask(
                    getContext(),
                    new QueryTask(
                            step.xpath, type, step.failIfNotFound, step.defaultValue, step.xpathOptions(), source));
        }
    }

    static final class QueryTask implements XmlTask {

        private static final long serialVersionUID = 1L;

        private final String xpath;
        private final ReturnType type;
        private final boolean failIfNotFound;
        private final String defaultValue;
        private final XPathOptions options;
        private final String source;

        QueryTask(
                String xpath,
                ReturnType type,
                boolean failIfNotFound,
                String defaultValue,
                XPathOptions options,
                String source) {
            this.xpath = xpath;
            this.type = type;
            this.failIfNotFound = failIfNotFound;
            this.defaultValue = defaultValue;
            this.options = options;
            this.source = source;
        }

        @Override
        public XmlTaskResult run(LDocument doc) throws XmlEditorException {
            XPathEngine engine = new XPathEngine(doc, options);
            Serializable value =
                    switch (type) {
                        case STRING -> {
                            Optional<String> s = engine.evaluateString(xpath);
                            if (s.isEmpty() && defaultValue != null) {
                                yield defaultValue;
                            }
                            if (s.isEmpty() && failIfNotFound) {
                                throw new XmlEditorException("XPath '" + xpath + "' matched nothing in " + source
                                        + " (use defaultValue: or failIfNotFound: false to get a value instead)");
                            }
                            yield s.orElse(null);
                        }
                        case LIST -> new ArrayList<>(engine.evaluateList(xpath));
                        case NUMBER -> engine.evaluateNumber(xpath);
                        case BOOLEAN -> engine.evaluateBoolean(xpath);
                    };
            return new XmlTaskResult(value, engine.warnings());
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "xmlQuery";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Query an XML file with XPath";
        }

        @POST
        public FormValidation doCheckXpath(@AncestorInPath Item item, @QueryParameter String value) {
            return XPathFormValidation.check(item, value, true);
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }

        public ListBoxModel doFillReturnTypeItems() {
            ListBoxModel items = new ListBoxModel();
            for (ReturnType t : ReturnType.values()) {
                items.add(t.name());
            }
            return items;
        }
    }
}
