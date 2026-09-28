package io.jenkins.plugins.xmleditor.steps;

import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.onAgentWithPom;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.run;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class XmlReadStepTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        PipelineSupport.agent(j);
    }

    @Test
    void readsTheWholeDocumentAsMapsAndLists() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  def pom = xmlRead file: 'pom.xml'
                  sleep time: 10, unit: 'MILLISECONDS'
                  echo "V=${pom.version} A=${pom.dependencies.dependency[1].artifactId} N=${pom.dependencies.dependency.size()}"
                """), Result.SUCCESS);
        j.assertLogContains("V=1.0 A=b N=2", b);
    }

    @Test
    void readsASubtreeSelectedByXPath() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  def dep = xmlRead file: 'pom.xml', xpath: '//dependency[2]'
                  echo "DEP=${dep.artifactId}"
                  def v = xmlRead text: '<a><b x="1">t</b></a>', xpath: '/a/b'
                  echo "ATTR=${v['@x']} TEXT=${v['#text']}"
                """), Result.SUCCESS);
        j.assertLogContains("DEP=b", b);
        j.assertLogContains("ATTR=1 TEXT=t", b);
    }

    @Test
    void xpathMustSelectAnElement() throws Exception {
        WorkflowRun none = run(j, "xmlRead text: '<a/>', xpath: '/nope'", Result.FAILURE);
        j.assertLogContains("/nope", none);
        WorkflowRun attr = run(j, "xmlRead text: '<a x=\"1\"/>', xpath: '/a/@x'", Result.FAILURE);
        j.assertLogContains("element", attr);
    }
}
