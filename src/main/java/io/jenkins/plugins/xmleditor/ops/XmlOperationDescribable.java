package io.jenkins.plugins.xmleditor.ops;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.core.edit.XmlOperation;
import java.util.function.UnaryOperator;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * An edit operation as configured in a Pipeline ({@code setText(xpath: ..., value: ...)}) or in the UI. It is
 * converted to the Jenkins-independent {@link XmlOperation} before running on the agent.
 */
public abstract class XmlOperationDescribable extends AbstractDescribableImpl<XmlOperationDescribable> {

    private final String xpath;
    private String expected;

    protected XmlOperationDescribable(String xpath) {
        this.xpath = xpath;
    }

    public String getXpath() {
        return xpath;
    }

    /** {@code ONE}, {@code AT_LEAST_ONE} (default when empty), {@code ANY} or a number. */
    @CheckForNull
    public String getExpected() {
        return expected;
    }

    @DataBoundSetter
    public void setExpected(String expected) {
        this.expected = Util.fixEmptyAndTrim(expected);
    }

    /**
     * @param expand expansion applied to user values (environment variables in Freestyle, identity in Pipeline)
     */
    public abstract XmlOperation toCore(UnaryOperator<String> expand);

    protected static String expand(UnaryOperator<String> expand, String value) {
        return value == null ? null : expand.apply(value);
    }

    /** Base descriptor, so that the UI lists all operations. */
    public abstract static class OperationDescriptor extends Descriptor<XmlOperationDescribable> {

        @POST
        public FormValidation doCheckXpath(@AncestorInPath Item item, @QueryParameter String value) {
            return XPathFormValidation.check(item, value, true);
        }
    }
}
