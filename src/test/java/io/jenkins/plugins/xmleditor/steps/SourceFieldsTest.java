package io.jenkins.plugins.xmleditor.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import io.jenkins.plugins.xmleditor.builders.XmlEditBuilder;
import io.jenkins.plugins.xmleditor.builders.XmlValidateBuilder;
import io.jenkins.plugins.xmleditor.ops.RemoveOp;
import java.net.URL;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** The source of the document ({@code file}, {@code files} or {@code text}) across steps and build steps. */
@WithJenkins
class SourceFieldsTest {

    @Test
    void validateStepHasNoNamespaceOptions(JenkinsRule j) throws Exception {
        DescribableModel<XmlValidateStep> validate = DescribableModel.of(XmlValidateStep.class);
        assertNull(validate.getParameter("namespaces"), "xmlValidate does not evaluate XPath");
        assertNull(validate.getParameter("strictNamespaces"), "xmlValidate does not evaluate XPath");
        assertNotNull(validate.getParameter("file"));
        assertNotNull(validate.getParameter("text"));
        assertNotNull(validate.getParameter("maxSizeMb"));

        for (Class<? extends AbstractXmlStep> type :
                List.of(XmlQueryStep.class, XmlReadStep.class, XmlEditStep.class)) {
            DescribableModel<?> model = DescribableModel.of(type);
            for (String name : List.of("file", "text", "maxSizeMb", "namespaces", "strictNamespaces")) {
                assertNotNull(model.getParameter(name), type.getSimpleName() + " should have " + name);
            }
        }
    }

    @Test
    void pathsAreTrimmedButXmlTextIsKeptAsIs(JenkinsRule j) {
        XmlQueryStep query = new XmlQueryStep("/a");
        query.setFile("  a.xml ");
        assertEquals("a.xml", query.getFile());
        query.setFile("   ");
        assertNull(query.getFile());
        query.setText("  <a/>\n");
        assertEquals("  <a/>\n", query.getText(), "the XML text must not be changed");
        query.setText("");
        assertNull(query.getText());

        XmlReadStep read = new XmlReadStep();
        read.setXpath(" /a/b ");
        assertEquals("/a/b", read.getXpath());

        XmlEditStep edit = new XmlEditStep(List.of(new RemoveOp("/a/b")));
        edit.setFiles(" **/*.xml ");
        edit.setExcludes(" target/** ");
        edit.setOutputFile(" out.xml ");
        edit.setText("\n<a><b/></a>\n");
        assertEquals("**/*.xml", edit.getFiles());
        assertEquals("target/**", edit.getExcludes());
        assertEquals("out.xml", edit.getOutputFile());
        assertEquals("\n<a><b/></a>\n", edit.getText());

        XmlValidateStep validate = new XmlValidateStep();
        validate.setSchema(" a.xsd ");
        assertEquals("a.xsd", validate.getSchema());

        XmlEditBuilder builder = new XmlEditBuilder(" pom.xml ", List.of(new RemoveOp("/a/b")));
        assertEquals("pom.xml", builder.getFile());
        XmlValidateBuilder validateBuilder = new XmlValidateBuilder(" pom.xml ");
        assertEquals("pom.xml", validateBuilder.getFile());
    }

    @Test
    void conflictingSourcesAreReportedInTheForm(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        XmlEditStep.DescriptorImpl edit = j.jenkins.getDescriptorByType(XmlEditStep.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, edit.doCheckFile(p, "a.xml", "", "").kind);
        assertEquals(FormValidation.Kind.OK, edit.doCheckFile(p, "", "*.xml", "<a/>").kind, "empty field: no error");
        assertError(edit.doCheckFile(p, "a.xml", "*.xml", ""), "'file', 'files' or 'text'");
        assertError(edit.doCheckFiles(p, "*.xml", "a.xml", ""), "'file', 'files' or 'text'");
        assertError(edit.doCheckText(p, "<a/>", "", "*.xml"), "'file', 'files' or 'text'");
        assertEquals(FormValidation.Kind.OK, edit.doCheckText(p, "<a/>", " ", "").kind);

        for (FileOrTextChecks step : List.<FileOrTextChecks>of(
                j.jenkins.getDescriptorByType(XmlQueryStep.DescriptorImpl.class)::doCheckFile,
                j.jenkins.getDescriptorByType(XmlReadStep.DescriptorImpl.class)::doCheckFile,
                j.jenkins.getDescriptorByType(XmlValidateStep.DescriptorImpl.class)::doCheckFile)) {
            assertEquals(FormValidation.Kind.OK, step.check(p, "a.xml", "").kind);
            assertError(step.check(p, "a.xml", "<a/>"), "'file' or 'text'");
        }
        for (FileOrTextChecks step : List.<FileOrTextChecks>of(
                j.jenkins.getDescriptorByType(XmlQueryStep.DescriptorImpl.class)::doCheckText,
                j.jenkins.getDescriptorByType(XmlReadStep.DescriptorImpl.class)::doCheckText,
                j.jenkins.getDescriptorByType(XmlValidateStep.DescriptorImpl.class)::doCheckText)) {
            assertEquals(FormValidation.Kind.OK, step.check(p, "<a/>", "").kind);
            assertError(step.check(p, "<a/>", "a.xml"), "'file' or 'text'");
        }

        XmlEditBuilder.DescriptorImpl builder = j.jenkins.getDescriptorByType(XmlEditBuilder.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, builder.doCheckFile(p, "pom.xml", "").kind);
        assertError(builder.doCheckFile(p, "pom.xml", "**/pom.xml"), "'file' or 'files'");
        assertError(builder.doCheckFiles(p, "**/pom.xml", "pom.xml"), "'file' or 'files'");
    }

    @Test
    void formChecksAreReachableOnlyWithPostAndBindTheOtherFields(JenkinsRule j) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        String url = "descriptorByName/" + XmlEditStep.class.getName() + "/checkFile?value=a.xml&files=*.xml&text=";
        WebRequest post = new WebRequest(new URL(j.getURL(), url), HttpMethod.POST);
        wc.addCrumb(post);
        String body = wc.getPage(post).getWebResponse().getContentAsString();
        assertTrue(body.contains("Use only one of"), body);

        wc.setThrowExceptionOnFailingStatusCode(false);
        int status = wc.getPage(new WebRequest(new URL(j.getURL(), url), HttpMethod.GET))
                .getWebResponse()
                .getStatusCode();
        assertEquals(404, status, "with @POST, Stapler does not route GET requests to the method");
    }

    @Test
    void usersWithoutConfigurePermissionGetNoFeedback(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ)
                .everywhere()
                .to("reader"));
        XmlEditStep.DescriptorImpl edit = j.jenkins.getDescriptorByType(XmlEditStep.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
            assertEquals(FormValidation.Kind.OK, edit.doCheckFile(p, "a.xml", "*.xml", "").kind);
            assertEquals(FormValidation.Kind.OK, edit.doCheckFile(null, "a.xml", "*.xml", "").kind);
        }
    }

    @FunctionalInterface
    private interface FileOrTextChecks {
        FormValidation check(Item item, String value, String other);
    }

    private static void assertError(FormValidation v, String expected) {
        assertEquals(FormValidation.Kind.ERROR, v.kind, v.renderHtml());
        assertTrue(v.renderHtml().contains(Util.escape(expected)), v.renderHtml());
    }
}
