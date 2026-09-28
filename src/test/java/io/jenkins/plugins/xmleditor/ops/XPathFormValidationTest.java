package io.jenkins.plugins.xmleditor.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.steps.XmlQueryStep;
import io.jenkins.plugins.xmleditor.steps.XmlReadStep;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class XPathFormValidationTest {

    @Test
    void xpathFieldsAreCheckedLive(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        SetTextOp.DescriptorImpl op = j.jenkins.getDescriptorByType(SetTextOp.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, op.doCheckXpath(p, "/project/version").kind);
        assertEquals(FormValidation.Kind.ERROR, op.doCheckXpath(p, "//[").kind);
        assertEquals(FormValidation.Kind.ERROR, op.doCheckXpath(p, "").kind);
        assertEquals(FormValidation.Kind.OK, op.doCheckXpath(p, "/a[@v='$VERSION']").kind, "variables not checked");

        XmlQueryStep.DescriptorImpl query = j.jenkins.getDescriptorByType(XmlQueryStep.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.ERROR, query.doCheckXpath(p, "/a/b[").kind);

        XmlReadStep.DescriptorImpl read = j.jenkins.getDescriptorByType(XmlReadStep.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, read.doCheckXpath(p, "").kind, "xpath is optional in xmlRead");
        assertEquals(FormValidation.Kind.ERROR, read.doCheckXpath(null, "//[").kind);
    }

    @Test
    void usersWithoutConfigurePermissionGetNoFeedback(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ)
                .everywhere()
                .to("reader")
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE)
                .everywhere()
                .to("dev"));
        SetTextOp.DescriptorImpl op = j.jenkins.getDescriptorByType(SetTextOp.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
            assertEquals(FormValidation.Kind.OK, op.doCheckXpath(p, "//[").kind);
            assertEquals(FormValidation.Kind.OK, op.doCheckXpath(null, "//[").kind);
        }
        try (ACLContext ignored = ACL.as2(User.getById("dev", true).impersonate2())) {
            assertEquals(FormValidation.Kind.ERROR, op.doCheckXpath(p, "//[").kind);
            assertEquals(FormValidation.Kind.OK, op.doCheckXpath(null, "//[").kind, "outside a job: admins only");
        }
    }
}
