package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.xmleditor.core.edit.Remove;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** {@code remove(xpath: ...)}: removes elements, attributes, comments or text nodes. */
public class RemoveOp extends XmlOperationDescribable {

    @DataBoundConstructor
    public RemoveOp(String xpath) {
        super(xpath);
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new Remove(expand(expand, getXpath()), getExpected());
    }

    @Extension
    @Symbol("remove")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Remove nodes";
        }
    }
}
