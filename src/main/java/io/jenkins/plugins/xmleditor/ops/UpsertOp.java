package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.xmleditor.core.edit.Upsert;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** {@code upsert(xpath: ..., value: ...)}: updates the nodes, or creates the path when it does not exist. */
public class UpsertOp extends XmlOperationDescribable {

    private final String value;

    @DataBoundConstructor
    public UpsertOp(String xpath, String value) {
        super(xpath);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new Upsert(expand(expand, getXpath()), getExpected(), expand(expand, value));
    }

    @Extension
    @Symbol("upsert")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Set value, creating the path if missing";
        }
    }
}
