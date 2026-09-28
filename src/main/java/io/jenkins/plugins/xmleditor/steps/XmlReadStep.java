package io.jenkins.plugins.xmleditor.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import io.jenkins.plugins.xmleditor.core.model.LElement;
import io.jenkins.plugins.xmleditor.core.model.LItem;
import io.jenkins.plugins.xmleditor.core.read.XmlToMapConverter;
import io.jenkins.plugins.xmleditor.core.xpath.XPathEngine;
import io.jenkins.plugins.xmleditor.core.xpath.XPathOptions;
import io.jenkins.plugins.xmleditor.ops.XPathFormValidation;
import java.util.List;
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

/** {@code xmlRead}: converts the document (or the element selected by {@code xpath}) to maps and lists. */
public class XmlReadStep extends AbstractXmlFileStep {

    private String xpath;

    @DataBoundConstructor
    public XmlReadStep() {}

    @CheckForNull
    public String getXpath() {
        return xpath;
    }

    @DataBoundSetter
    public void setXpath(String xpath) {
        this.xpath = xpath == null || xpath.isBlank() ? null : xpath;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Object> {

        private static final long serialVersionUID = 1L;

        private final transient XmlReadStep step;

        Execution(XmlReadStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            String source = step.getFile() != null ? step.getFile() : "text";
            return step.runTask(getContext(), new ReadTask(step.xpath, step.xpathOptions(), source));
        }
    }

    static final class ReadTask implements XmlTask {

        private static final long serialVersionUID = 1L;

        private final String xpath;
        private final XPathOptions options;
        private final String source;

        ReadTask(String xpath, XPathOptions options, String source) {
            this.xpath = xpath;
            this.options = options;
            this.source = source;
        }

        @Override
        public XmlTaskResult run(LDocument doc) throws XmlEditorException {
            if (xpath == null) {
                return new XmlTaskResult(XmlToMapConverter.convert(doc.root()), List.of());
            }
            XPathEngine engine = new XPathEngine(doc, options);
            List<LItem> items = engine.selectNodes(xpath);
            if (items.isEmpty()) {
                throw new XmlEditorException("XPath '" + xpath + "' matched nothing in " + source);
            }
            if (!(items.get(0) instanceof LElement element)) {
                throw new XmlEditorException("XPath '" + xpath + "' must select an element");
            }
            return new XmlTaskResult(XmlToMapConverter.convert(element), engine.warnings());
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "xmlRead";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Read an XML file into maps and lists";
        }

        @POST
        public FormValidation doCheckXpath(@AncestorInPath Item item, @QueryParameter String value) {
            return XPathFormValidation.check(item, value, false);
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
