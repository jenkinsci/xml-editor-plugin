package io.jenkins.plugins.xmleditor.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import io.jenkins.plugins.xmleditor.ops.AddElementOp;
import io.jenkins.plugins.xmleditor.ops.RemoveOp;
import io.jenkins.plugins.xmleditor.ops.SetAttributeOp;
import io.jenkins.plugins.xmleditor.ops.SetTextOp;
import io.jenkins.plugins.xmleditor.ops.XmlOperationDescribable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FreestyleBuildersTest {

    private static final String POM = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <!-- keep me -->\n"
            + "  <version>1.0</version>\n"
            + "</project>\n";

    private static final String XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "<xs:element name=\"port\" type=\"xs:int\"/></xs:schema>";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        j.createOnlineSlave(Label.get("xml-agent"));
        EnvironmentVariablesNodeProperty env =
                new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("NEW_VERSION", "3.0"));
        j.jenkins.getGlobalNodeProperties().add(env);
    }

    private FreeStyleProject projectWriting(String file, String content) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.get("xml-agent"));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws java.io.IOException, InterruptedException {
                build.getWorkspace().child(file).write(content, StandardCharsets.UTF_8.name());
                return true;
            }
        });
        return p;
    }

    @Test
    void editBuilderChangesTheFileOnTheAgentWithEnvironmentVariables() throws Exception {
        FreeStyleProject p = projectWriting("pom.xml", POM);
        p.getBuildersList()
                .add(new XmlEditBuilder("pom.xml", List.of(new SetTextOp("/project/version", "$NEW_VERSION"))));
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertEquals(
                POM.replace("1.0", "3.0"), b.getWorkspace().child("pom.xml").readToString());
        j.assertLogContains("+  <version>3.0</version>", b);
    }

    @Test
    void editBuilderAcceptsNamespacePrefixes() throws Exception {
        FreeStyleProject p = projectWriting("pom.xml", POM);
        XmlEditBuilder edit = new XmlEditBuilder("pom.xml", List.of(new SetTextOp("/m:project/m:version", "4.0")));
        edit.setNamespaces("m=http://maven.apache.org/POM/4.0.0");
        p.getBuildersList().add(edit);
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertEquals(
                POM.replace("1.0", "4.0"), b.getWorkspace().child("pom.xml").readToString());

        edit.setNamespaces("not a declaration");
        j.assertLogContains("line 1", j.buildAndAssertStatus(Result.FAILURE, p));
    }

    @Test
    void editBuilderWithFilePatternAndDryRun() throws Exception {
        FreeStyleProject p = projectWriting("a/pom.xml", POM);
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws java.io.IOException, InterruptedException {
                build.getWorkspace().child("b/pom.xml").write(POM, "UTF-8");
                return true;
            }
        });
        XmlEditBuilder dry = new XmlEditBuilder("", List.of(new SetTextOp("/project/version", "5.0")));
        dry.setFiles("**/pom.xml");
        dry.setDryRun(true);
        p.getBuildersList().add(dry);
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        j.assertLogContains("b/pom.xml would be updated (dry run)", b);
        assertEquals(POM, b.getWorkspace().child("a/pom.xml").readToString());

        dry.setDryRun(false);
        b = j.buildAndAssertSuccess(p);
        assertEquals(
                POM.replace("1.0", "5.0"), b.getWorkspace().child("b/pom.xml").readToString());
    }

    @Test
    void editBuilderFailsTheBuildOnErrors() throws Exception {
        FreeStyleProject p = projectWriting("pom.xml", POM);
        p.getBuildersList().add(new XmlEditBuilder("pom.xml", List.of(new RemoveOp("/project/nope"))));
        FreeStyleBuild b = j.buildAndAssertStatus(Result.FAILURE, p);
        j.assertLogContains("Operation 1 (remove)", b);
    }

    @Test
    void editBuilderNeedsExactlyOneOfFileOrFiles() throws Exception {
        FreeStyleProject p = projectWriting("pom.xml", POM);
        XmlEditBuilder edit = new XmlEditBuilder("", List.of(new RemoveOp("/project/version")));
        p.getBuildersList().add(edit);
        FreeStyleBuild none = j.buildAndAssertStatus(Result.FAILURE, p);
        j.assertLogContains("Edit XML file: specify exactly one of 'file' or 'files'", none);
        j.assertLogNotContains("'text'", none);

        XmlEditBuilder both = new XmlEditBuilder("pom.xml", List.of(new RemoveOp("/project/version")));
        both.setFiles("**/*.xml");
        p.getBuildersList().replace(edit, both);
        j.assertLogContains(
                "Edit XML file: specify exactly one of 'file' or 'files'", j.buildAndAssertStatus(Result.FAILURE, p));
    }

    @Test
    void configurationRoundTrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        SetTextOp setText = new SetTextOp("/project/version", "2.0");
        setText.setExpected("ONE");
        AddElementOp add = new AddElementOp("/project", "<x/>");
        add.setPosition("FIRST_CHILD");
        List<XmlOperationDescribable> ops = List.of(setText, new SetAttributeOp("/project", "a", "b"), add);
        XmlEditBuilder edit = new XmlEditBuilder("pom.xml", ops);
        edit.setOutputFile("out.xml");
        edit.setShowDiff(false);
        edit.setNamespaces("m=urn:m");
        XmlValidateBuilder validate = new XmlValidateBuilder("out.xml");
        validate.setSchema("schema.xsd");
        p.getBuildersList().add(edit);
        p.getBuildersList().add(validate);
        XmlEditBuilder many = new XmlEditBuilder("", List.of(new RemoveOp("//x")));
        many.setFiles("**/*.xml");
        many.setExcludes("target/**");
        many.setDryRun(true);
        p.getBuildersList().add(many);
        j.configRoundtrip(p);
        List<XmlEditBuilder> edits = p.getBuildersList().getAll(XmlEditBuilder.class);
        j.assertEqualDataBoundBeans(edit, edits.get(0));
        j.assertEqualDataBoundBeans(many, edits.get(1));
        j.assertEqualDataBoundBeans(validate, p.getBuildersList().get(XmlValidateBuilder.class));
    }

    @Test
    void validateBuilder() throws Exception {
        FreeStyleProject ok = projectWriting("schema.xsd", XSD);
        ok.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws java.io.IOException, InterruptedException {
                build.getWorkspace().child("good.xml").write("<port>1</port>", "UTF-8");
                build.getWorkspace().child("bad.xml").write("<port>x</port>", "UTF-8");
                return true;
            }
        });
        XmlValidateBuilder good = new XmlValidateBuilder("good.xml");
        good.setSchema("schema.xsd");
        ok.getBuildersList().add(good);
        j.assertLogContains("good.xml is valid", j.buildAndAssertSuccess(ok));

        XmlValidateBuilder bad = new XmlValidateBuilder("bad.xml");
        bad.setSchema("schema.xsd");
        ok.getBuildersList().add(bad);
        FreeStyleBuild b = j.buildAndAssertStatus(Result.FAILURE, ok);
        j.assertLogContains("bad.xml is not valid", b);
    }
}
