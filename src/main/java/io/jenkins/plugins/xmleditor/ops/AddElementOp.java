package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.xmleditor.core.edit.AddElement;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** {@code addElement(xpath: ..., fragment: ..., position: 'LAST_CHILD')}: inserts an XML fragment. */
public class AddElementOp extends XmlOperationDescribable {

    static final String DEFAULT_POSITION = "LAST_CHILD";

    private final String fragment;
    private String position = DEFAULT_POSITION;

    @DataBoundConstructor
    public AddElementOp(String xpath, String fragment) {
        super(xpath);
        this.fragment = fragment;
    }

    public String getFragment() {
        return fragment;
    }

    public String getPosition() {
        return position;
    }

    @DataBoundSetter
    public void setPosition(String position) {
        this.position = position == null || position.isBlank() ? DEFAULT_POSITION : position.trim();
    }

    @Override
    public XmlOperation toCore(UnaryOperator<String> expand) {
        return new AddElement(expand(expand, getXpath()), getExpected(), expand(expand, fragment), position);
    }

    @Extension
    @Symbol("addElement")
    public static class DescriptorImpl extends OperationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Add element";
        }

        // Fixed list of options, the same information is in the source code: no POST or permission check needed.
        @SuppressWarnings({"lgtm[jenkins/csrf]", "lgtm[jenkins/no-permission-check]"})
        public ListBoxModel doFillPositionItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("As last child", "LAST_CHILD"),
                    new ListBoxModel.Option("As first child", "FIRST_CHILD"),
                    new ListBoxModel.Option("Before the selected element", "BEFORE"),
                    new ListBoxModel.Option("After the selected element", "AFTER"));
        }
    }
}
