package io.jenkins.plugins.xmleditor.steps;

import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.groovy;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.run;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class XmlValidateStepTest {

    static final String XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
            + "  <xs:element name=\"config\"><xs:complexType><xs:sequence>\n"
            + "    <xs:element name=\"port\" type=\"xs:int\"/>\n"
            + "  </xs:sequence></xs:complexType></xs:element>\n"
            + "</xs:schema>\n";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        PipelineSupport.agent(j);
    }

    private static String onAgent(String body) {
        return "node('" + PipelineSupport.AGENT + "') {\n"
                + "  writeFile file: 'schema/config.xsd', text: " + groovy(XSD) + "\n"
                + "  writeFile file: 'good.xml', text: '<config><port>80</port></config>'\n"
                + "  writeFile file: 'bad.xml', text: '<config>\\n  <port>abc</port>\\n</config>'\n"
                + body + "\n}";
    }

    @Test
    void validAndInvalidFilesWithoutFailing() throws Exception {
        WorkflowRun b = run(j, onAgent("""
                  def ok = xmlValidate file: 'good.xml', schema: 'schema/config.xsd'
                  def ko = xmlValidate file: 'bad.xml', schema: 'schema/config.xsd', failOnError: false
                  echo "OK=${ok.valid} KO=${ko.valid} LINE=${ko.errors[0].line} SEV=${ko.errors[0].severity}"
                  def wf = xmlValidate text: '<a><b></a>', failOnError: false
                  echo "WF=${wf.valid} ${wf.errors[0].severity}"
                """), Result.SUCCESS);
        j.assertLogContains("OK=true KO=false LINE=2 SEV=ERROR", b);
        j.assertLogContains("WF=false FATAL", b);
        j.assertLogContains("good.xml is valid", b);
    }

    @Test
    void invalidFileFailsTheBuildByDefault() throws Exception {
        WorkflowRun b = run(j, onAgent("xmlValidate file: 'bad.xml', schema: 'schema/config.xsd'"), Result.FAILURE);
        j.assertLogContains("bad.xml is not valid", b);
        j.assertLogContains("2:", b);
    }

    @Test
    void schemaOutsideTheWorkspaceIsRefused() throws Exception {
        WorkflowRun b = run(j, onAgent("xmlValidate file: 'good.xml', schema: '../x.xsd'"), Result.FAILURE);
        j.assertLogContains("outside the workspace", b);
    }
}
