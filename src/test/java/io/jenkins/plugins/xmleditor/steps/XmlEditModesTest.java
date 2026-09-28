package io.jenkins.plugins.xmleditor.steps;

import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.onAgentWithPom;
import static io.jenkins.plugins.xmleditor.steps.PipelineSupport.run;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** xmlEdit with several files, dry run and in-memory text. */
@WithJenkins
class XmlEditModesTest {

    private static final String CSPROJ =
            "<Project Sdk=\"Microsoft.NET.Sdk\">\\n  <PropertyGroup>\\n    <Version>1.0</Version>\\n  </PropertyGroup>\\n</Project>\\n";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        PipelineSupport.agent(j);
    }

    private static String onAgent(String body) {
        return "node('" + PipelineSupport.AGENT + "') {\n" + body + "\n}";
    }

    @Test
    void severalFilesWithIncludesAndExcludes() throws Exception {
        WorkflowRun b = run(j, onAgent("""
                  ['a', 'b', 'c', 'excluded'].each { writeFile file: "${it}/App.csproj", text: '%s' }
                  def res = xmlEdit files: '**/*.csproj', excludes: 'excluded/**', operations: [
                    setText(xpath: '/Project/PropertyGroup/Version', value: '2.0')
                  ]
                  echo "FILES=${res.files.collect { it.file }.join(',')} CHANGED=${res.changed} OLD=${res.files[0].operations[0].oldValues}"
                  echo "EXCLUDED=${xmlQuery file: 'excluded/App.csproj', xpath: '//Version'} B=${xmlQuery file: 'b/App.csproj', xpath: '//Version'}"
                """.formatted(CSPROJ)), Result.SUCCESS);
        j.assertLogContains("FILES=a/App.csproj,b/App.csproj,c/App.csproj CHANGED=true OLD=[1.0]", b);
        j.assertLogContains("EXCLUDED=1.0 B=2.0", b);
    }

    @Test
    void oneBrokenFileMeansNoFileIsWritten() throws Exception {
        WorkflowRun b = run(j, onAgent("""
                  writeFile file: 'a/App.csproj', text: '%s'
                  writeFile file: 'b/App.csproj', text: '<Project><broken></Project>'
                  writeFile file: 'c/App.csproj', text: '%s'
                  try {
                    xmlEdit files: '**/*.csproj', operations: [setText(xpath: '//Version', value: '2.0')]
                  } catch (e) {
                    echo "CAUGHT: ${e.message}"
                  }
                  echo "A=${xmlQuery file: 'a/App.csproj', xpath: '//Version'} C=${xmlQuery file: 'c/App.csproj', xpath: '//Version'}"
                """.formatted(CSPROJ, CSPROJ)), Result.SUCCESS);
        j.assertLogContains("CAUGHT: b/App.csproj", b);
        j.assertLogContains("A=1.0 C=1.0", b);
    }

    @Test
    void expectationsApplyToEachFileAndErrorsNameTheFile() throws Exception {
        WorkflowRun b = run(j, onAgent("""
                  writeFile file: 'a/App.csproj', text: '%s'
                  writeFile file: 'b/App.csproj', text: '<Project/>'
                  xmlEdit files: '**/*.csproj', operations: [setText(xpath: '//Version', value: '2.0')]
                """.formatted(CSPROJ)), Result.FAILURE);
        j.assertLogContains("b/App.csproj: Operation 1 (setText)", b);
    }

    @Test
    void patternsAreRelativeToTheCurrentDirectoryAndMustMatch() throws Exception {
        WorkflowRun ok = run(j, onAgent("""
                  writeFile file: 'src/App.csproj', text: '%s'
                  writeFile file: 'other/App.csproj', text: '%s'
                  dir('src') {
                    def res = xmlEdit files: '*.csproj', operations: [setText(xpath: '//Version', value: '3.0')]
                    echo "COUNT=${res.files.size()}"
                  }
                """.formatted(CSPROJ, CSPROJ)), Result.SUCCESS);
        j.assertLogContains("COUNT=1", ok);
        WorkflowRun none =
                run(j, onAgent("xmlEdit files: '**/*.nothing', operations: [remove(xpath: '/a')]"), Result.FAILURE);
        j.assertLogContains("No files match", none);
    }

    @Test
    void dryRunShowsTheDiffWithoutWriting() throws Exception {
        WorkflowRun b = run(j, onAgentWithPom("""
                  def res = xmlEdit file: 'pom.xml', dryRun: true, operations: [setText(xpath: '/project/version', value: '9.9')]
                  echo "DRY=${res.dryRun} CHANGED=${res.changed} NOW=${xmlQuery file: 'pom.xml', xpath: '/project/version'}"
                """), Result.SUCCESS);
        j.assertLogContains("DRY=true CHANGED=true NOW=1.0", b);
        j.assertLogContains("+  <version>9.9</version>", b);
        j.assertLogContains("pom.xml would be updated (dry run)", b);
    }

    @Test
    void textIsEditedInMemoryWithoutAWorkspace() throws Exception {
        WorkflowRun b = run(j, """
                def res = xmlEdit text: '<a>\\n  <!-- keep -->\\n  <b>1</b>\\n</a>', operations: [setText(xpath: '/a/b', value: '2')]
                echo "TEXT=${res.text.replace('\\n', '|')} CHANGED=${res.changed}"
                """, Result.SUCCESS);
        j.assertLogContains("TEXT=<a>|  <!-- keep -->|  <b>2</b>|</a> CHANGED=true", b);
    }

    @Test
    void invalidCombinationsAreRejected() throws Exception {
        String op = "operations: [remove(xpath: '/a/b')]";
        j.assertLogContains(
                "exactly one of", run(j, onAgent("xmlEdit file: 'a.xml', files: '*.xml', " + op), Result.FAILURE));
        j.assertLogContains(
                "outputFile", run(j, onAgent("xmlEdit files: '*.xml', outputFile: 'o.xml', " + op), Result.FAILURE));
        j.assertLogContains("dryRun", run(j, "xmlEdit text: '<a/>', dryRun: true, " + op, Result.FAILURE));
        j.assertLogContains("excludes", run(j, onAgent("xmlEdit file: 'a.xml', excludes: 'x', " + op), Result.FAILURE));
    }
}
