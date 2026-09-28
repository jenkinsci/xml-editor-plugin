package io.jenkins.plugins.xmleditor.steps;

import hudson.model.Label;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jvnet.hudson.test.JenkinsRule;

/** Helpers to run Pipelines on a real agent in tests. */
final class PipelineSupport {

    static final String AGENT = "xml-agent";

    static final String POM = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <!-- the version -->\n"
            + "  <version>1.0</version>\n"
            + "  <dependencies>\n"
            + "    <dependency><artifactId>a</artifactId></dependency>\n"
            + "    <dependency><artifactId>b</artifactId></dependency>\n"
            + "  </dependencies>\n"
            + "</project>\n";

    private PipelineSupport() {}

    static void agent(JenkinsRule j) throws Exception {
        j.createOnlineSlave(Label.get(AGENT));
    }

    /** Groovy literal for a text: triple single quotes, with backslashes and quotes escaped. */
    static String groovy(String text) {
        return "'''" + text.replace("\\", "\\\\").replace("'''", "\\'\\'\\'") + "'''";
    }

    /** A scripted Pipeline on the test agent that first writes {@code pom.xml}. */
    static String onAgentWithPom(String body) {
        return "node('" + AGENT + "') {\n  writeFile file: 'pom.xml', text: " + groovy(POM) + "\n" + body + "\n}";
    }

    static WorkflowRun run(JenkinsRule j, String script, Result expected) throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(script, true));
        return j.buildAndAssertStatus(expected, p);
    }
}
