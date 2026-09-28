package io.jenkins.plugins.xmleditor.steps;

import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.groovy;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.onAgentWithPom;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.run;

import hudson.model.Result;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class XmlEditStepTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        PipelineSupport.agent(j);
    }

    @Test
    void upsertCreatesMissingNodes() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  xmlEdit file: 'pom.xml', operations: [
                    upsert(xpath: '/project/properties/revision', value: '7'),
                    upsert(xpath: '/project/version', value: '1.5')
                  ]
                  echo "REV=${xmlQuery file: 'pom.xml', xpath: '/project/properties/revision'} V=${xmlQuery file: 'pom.xml', xpath: '/project/version'}"
                """), Result.SUCCESS);
        j.assertLogContains("REV=7 V=1.5", b);
        j.assertLogContains("+  <properties>", b);
    }

    @Test
    void oldValuesReachThePipelineIncludingNulls() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  def res = xmlEdit file: 'pom.xml', operations: [
                    setText(xpath: '/project/version', value: '1.1'),
                    setAttribute(xpath: '/project', name: 'foo', value: 'bar')
                  ]
                  sleep time: 10, unit: 'MILLISECONDS'
                  echo "OLD=${res.operations[0].oldValues} NULLS=${res.operations[1].oldValues}"
                """), Result.SUCCESS);
        j.assertLogContains("OLD=[1.0] NULLS=[null]", b);
    }

    @Test
    void versionBumpChangesOnlyTheVersion() throws Exception {
        String expected = PipelineSupport.POM.replace("<version>1.0</version>", "<version>1.1</version>");
        WorkflowRun b = run(j, onAgentWithPom("""
                  def res = xmlEdit file: 'pom.xml', operations: [setText(xpath: '/project/version', value: '1.1')]
                  echo "CHANGED=${res.changed} MODIFIED=${res.operations[0].modified} TYPE=${res.operations[0].type}"
                  if (readFile('pom.xml') != %s) { error 'CONTENT MISMATCH' }
                """.formatted(groovy(expected))), Result.SUCCESS);
        j.assertLogContains("CHANGED=true MODIFIED=1 TYPE=setText", b);
        j.assertLogContains("-  <version>1.0</version>", b);
        j.assertLogContains("+  <version>1.1</version>", b);
    }

    @Test
    void declarativeWithSeveralOperations() throws Exception {
        WorkflowRun b = run(j, """
                pipeline {
                  agent { label '%s' }
                  stages {
                    stage('edit') {
                      steps {
                        writeFile file: 'pom.xml', text: %s
                        xmlEdit file: 'pom.xml', showDiff: false, operations: [
                          addElement(xpath: '/project/dependencies', fragment: '<dependency><artifactId>c</artifactId></dependency>'),
                          remove(xpath: "//dependency[artifactId='a']"),
                          setAttribute(xpath: '/project', name: 'foo', value: 'bar'),
                          removeAttribute(xpath: '/project', name: 'foo', expected: 'ONE')
                        ]
                        script {
                          def ids = xmlQuery file: 'pom.xml', xpath: '//dependency/artifactId', returnType: 'LIST'
                          echo "IDS=${ids.join(',')}"
                        }
                      }
                    }
                  }
                }
                """.formatted(PipelineSupport.AGENT, groovy(PipelineSupport.POM)), Result.SUCCESS);
        j.assertLogContains("IDS=b,c", b);
        j.assertLogNotContains("+++ b/pom.xml", b);
    }

    @Test
    void byteOrderMarkAndCrlfArePreserved() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String before = "<Project Sdk=\"Microsoft.NET.Sdk\">\r\n  <PropertyGroup>\r\n    <Version>1.2.3</Version>\r\n"
                + "  </PropertyGroup>\r\n</Project>\r\n";
        String after = before.replace("1.2.3", "1.3.0");
        WorkflowRun b =
                run(j, """
                node('%s') {
                  writeFile file: 'app.csproj', text: '%s', encoding: 'Base64'
                  xmlEdit file: 'app.csproj', operations: [setText(xpath: '//Version', value: '1.3.0')]
                  def actual = readFile file: 'app.csproj', encoding: 'Base64'
                  echo(actual == '%s' ? 'BYTES-OK' : 'BYTES-DIFFER ' + actual)
                }
                """.formatted(PipelineSupport.AGENT, base64(bom, before), base64(bom, after)), Result.SUCCESS);
        j.assertLogContains("BYTES-OK", b);
    }

    @Test
    void outputFileLeavesTheOriginalUntouched() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  xmlEdit file: 'pom.xml', outputFile: 'out/pom.xml', operations: [setText(xpath: '/project/version', value: '9')]
                  echo "ORIG=${xmlQuery file: 'pom.xml', xpath: '/project/version'} OUT=${xmlQuery file: 'out/pom.xml', xpath: '/project/version'}"
                """), Result.SUCCESS);
        j.assertLogContains("ORIG=1.0 OUT=9", b);
    }

    @Test
    void failedExpectationLeavesTheFileUnchanged() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  try {
                    xmlEdit file: 'pom.xml', operations: [
                      setText(xpath: '/project/version', value: '2.0'),
                      remove(xpath: '//dependency', expected: 'ONE')
                    ]
                  } catch (e) {
                    echo "CAUGHT: ${e.message}"
                  }
                  echo "VERSION=${xmlQuery file: 'pom.xml', xpath: '/project/version'}"
                """), Result.SUCCESS);
        j.assertLogContains("CAUGHT: Operation 2 (remove)", b);
        j.assertLogContains("matched 2 node(s), expected exactly 1", b);
        j.assertLogContains("VERSION=1.0", b);
    }

    @Test
    void noEffectiveChangeDoesNotRewrite() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  def res = xmlEdit file: 'pom.xml', operations: [setText(xpath: '/project/version', value: '1.0')]
                  echo "CHANGED=${res.changed}"
                """), Result.SUCCESS);
        j.assertLogContains("CHANGED=false", b);
        j.assertLogContains("pom.xml unchanged", b);
    }

    @Test
    void failuresOutsideTryFailTheBuild() throws Exception {
        WorkflowRun b = run(
                j,
                onAgentWithPom("xmlEdit file: 'pom.xml', operations: [setText(xpath: '/project/nope', value: 'x')]"),
                Result.FAILURE);
        j.assertLogContains("Operation 1 (setText): XPath '/project/nope' matched 0 node(s)", b);
        WorkflowRun empty = run(j, onAgentWithPom("xmlEdit file: 'pom.xml', operations: []"), Result.FAILURE);
        j.assertLogContains("at least one operation", empty);
    }

    private static String base64(byte[] prefix, String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, all, 0, prefix.length);
        System.arraycopy(body, 0, all, prefix.length, body.length);
        return Base64.getEncoder().encodeToString(all);
    }
}
