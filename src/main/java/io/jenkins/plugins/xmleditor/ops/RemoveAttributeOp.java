package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.xmleditor.core.edit.RemoveAttribute;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** {@code removeAttribute(xpath: ..., name: ...)}: removes an attribute when present. */
public class RemoveAttributeOp extends XmlOperationDescribable {

    private final String name;

    @DataBoundConstructor
    public RemoveAttributeOp(String xpath, String name) {
        super(xpath);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new RemoveAttribute(expand(expand, getXpath()), getExpected(), expand(expand, name));
    }

    @Extension
    @Symbol("removeAttribute")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Remove attribute";
        }
    }
}
