package io.jenkins.plugins.xmleditor.steps;

import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.groovy;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.onAgentWithPom;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.run;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class XmlQueryStepTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        PipelineSupport.agent(j);
    }

    @Test
    void scriptedPipelineOnAgentWithAllReturnTypes() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  echo "VERSION=${xmlQuery file: 'pom.xml', xpath: '/project/version'}"
                  def ids = xmlQuery file: 'pom.xml', xpath: '//dependency/artifactId', returnType: 'LIST'
                  echo "IDS=${ids.join(',')} SIZE=${ids.size()}"
                  echo "COUNT=${xmlQuery file: 'pom.xml', xpath: 'count(//dependency)', returnType: 'NUMBER'}"
                  echo "HAS=${xmlQuery file: 'pom.xml', xpath: 'boolean(//dependency)', returnType: 'BOOLEAN'}"
                  echo "MISSING=${xmlQuery file: 'pom.xml', xpath: '/project/nope', failIfNotFound: false}"
                  echo "EMPTY=${xmlQuery(file: 'pom.xml', xpath: '//nope', returnType: 'LIST').size()}"
                """), Result.SUCCESS);
        j.assertLogContains("VERSION=1.0", b);
        j.assertLogContains("IDS=a,b SIZE=2", b);
        j.assertLogContains("COUNT=2.0", b);
        j.assertLogContains("HAS=true", b);
        j.assertLogContains("MISSING=null", b);
        j.assertLogContains("EMPTY=0", b);
    }

    @Test
    void defaultValueIsReturnedWhenNothingMatches() throws Exception {
        WorkflowRun b = run(j, """
                def missing = xmlQuery text: '<a/>', xpath: '/a/version', defaultValue: '0.0.0'
                def present = xmlQuery text: '<a><version>2</version></a>', xpath: '/a/version', defaultValue: '0.0.0'
                def strict = xmlQuery text: '<a/>', xpath: '/a/version', defaultValue: 'd', failIfNotFound: true
                echo "MISSING=${missing} PRESENT=${present} STRICT=${strict}"
                """, Result.SUCCESS);
        j.assertLogContains("MISSING=0.0.0 PRESENT=2 STRICT=d", b);
    }

    @Test
    void declarativePipeline() throws Exception {
        WorkflowRun b = run(j, """
                pipeline {
                  agent { label '%s' }
                  stages {
                    stage('query') {
                      steps {
                        writeFile file: 'pom.xml', text: %s
                        script {
                          def v = xmlQuery file: 'pom.xml', xpath: '/project/version'
                          echo "DECLARATIVE=${v}"
                        }
                      }
                    }
                  }
                }
                """.formatted(PipelineSupport.AGENT, groovy(PipelineSupport.POM)), Result.SUCCESS);
        j.assertLogContains("DECLARATIVE=1.0", b);
    }

    @Test
    void noMatchFailsTheBuildWithAClearMessage() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("xmlQuery file: 'pom.xml', xpath: '/project/nope'"), Result.FAILURE);
        j.assertLogContains("/project/nope", b);
        j.assertLogContains("pom.xml", b);
        j.assertLogNotContains("at io.jenkins.plugins", b);
    }

    @Test
    void textParameterAndNamespaces() throws Exception {
        WorkflowRun b = run(j, """
                def v = xmlQuery text: '<r xmlns="urn:x"><a>1</a></r>', xpath: '/r/a'
                def w = xmlQuery text: '<r xmlns="urn:x"><a>2</a></r>', xpath: '/x:r/x:a', namespaces: [x: 'urn:x']
                echo "TEXT=${v}${w}"
                """, Result.SUCCESS);
        j.assertLogContains("TEXT=12", b);
    }

    @Test
    void fileAndTextAreMutuallyExclusive() throws Exception {
        WorkflowRun both = run(j, "xmlQuery text: '<a/>', file: 'a.xml', xpath: '/a'", Result.FAILURE);
        j.assertLogContains("exactly one of", both);
        WorkflowRun none = run(j, "xmlQuery xpath: '/a'", Result.FAILURE);
        j.assertLogContains("exactly one of", none);
    }

    @Test
    void pathIsRelativeToTheCurrentDirectoryAndConfined() throws Exception {
        WorkflowRun ok = run(j, onAgentWithPom("""
                  dir('sub') {
                    writeFile file: 'pom.xml', text: '<project><version>2.0</version></project>'
                    echo "SUB=${xmlQuery file: 'pom.xml', xpath: '/project/version'}"
                  }
                """), Result.SUCCESS);
        j.assertLogContains("SUB=2.0", ok);
        WorkflowRun escape = run(j, onAgentWithPom("xmlQuery file: '../x.xml', xpath: '/a'"), Result.FAILURE);
        j.assertLogContains("outside", escape);
    }

    @Test
    void invalidReturnTypeIsRejected() throws Exception {
        WorkflowRun b = run(j, "xmlQuery text: '<a/>', xpath: '/a', returnType: 'MAP'", Result.FAILURE);
        j.assertLogContains("returnType", b);
    }

    @Test
    void malformedFileReportsLine() throws Exception {
        WorkflowRun b = run(j, """
                node('%s') {
                  writeFile file: 'bad.xml', text: '<a>\\n<b>\\n</a>'
                  xmlQuery file: 'bad.xml', xpath: '/a'
                }
                """.formatted(PipelineSupport.AGENT), Result.FAILURE);
        j.assertLogContains("bad.xml:3:", b);
    }
}
