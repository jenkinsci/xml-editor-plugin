package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.xmleditor.core.edit.SetAttribute;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** {@code setAttribute(xpath: ..., name: ..., value: ...)}: adds or updates an attribute. */
public class SetAttributeOp extends XmlOperationDescribable {

    private final String name;
    private final String value;

    @DataBoundConstructor
    public SetAttributeOp(String xpath, String name, String value) {
        super(xpath);
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new SetAttribute(expand(expand, getXpath()), getExpected(), expand(expand, name), expand(expand, value));
    }

    @Extension
    @Symbol("setAttribute")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Set attribute";
        }
    }
}
