package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.xmleditor.core.edit.SetText;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** {@code setText(xpath: ..., value: ...)}: sets element text, attribute values or text nodes. */
public class SetTextOp extends XmlOperationDescribable {

    private final String value;

    @DataBoundConstructor
    public SetTextOp(String xpath, String value) {
        super(xpath);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new SetText(expand(expand, getXpath()), getExpected(), expand(expand, value));
    }

    @Extension
    @Symbol("setText")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Set text or attribute value";
        }
    }
}
