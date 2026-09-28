package io.jenkins.plugins.xmleditor.steps;

import io.jenkins.plugins.xmleditor.ops.AddElementOp;
import io.jenkins.plugins.xmleditor.ops.RemoveOp;
import io.jenkins.plugins.xmleditor.ops.SetTextOp;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** The steps must be representable by the Snippet Generator and parse back to the same configuration. */
@WithJenkins
class SnippetGeneratorTest {

    @Test
    void stepsRoundTrip(JenkinsRule j) throws Exception {
        SnippetizerTester st = new SnippetizerTester(j);

        XmlQueryStep query = new XmlQueryStep("/m:project/m:version");
        query.setFile("pom.xml");
        query.setReturnType("LIST");
        query.setNamespaces(Map.of("m", "http://maven.apache.org/POM/4.0.0"));
        st.assertRoundTrip(
                query,
                "xmlQuery file: 'pom.xml', namespaces: [m: 'http://maven.apache.org/POM/4.0.0'],"
                        + " returnType: 'LIST', xpath: '/m:project/m:version'");

        XmlReadStep read = new XmlReadStep();
        read.setFile("pom.xml");
        st.assertRoundTrip(read, "xmlRead file: 'pom.xml'");

        SetTextOp setText = new SetTextOp("/project/version", "1.1");
        setText.setExpected("ONE");
        AddElementOp add = new AddElementOp("/project", "<x/>");
        add.setPosition("FIRST_CHILD");
        XmlEditStep edit = new XmlEditStep(List.of(setText, add, new RemoveOp("//old")));
        edit.setFile("pom.xml");
        edit.setShowDiff(false);
        st.assertRoundTrip(
                edit,
                "xmlEdit file: 'pom.xml', operations: [setText(expected: 'ONE', value: '1.1', xpath: '/project/version'),"
                        + " addElement(fragment: '<x/>', position: 'FIRST_CHILD', xpath: '/project'),"
                        + " remove('//old')], showDiff: false");

        XmlValidateStep validate = new XmlValidateStep();
        validate.setFile("a.xml");
        validate.setSchema("a.xsd");
        validate.setFailOnError(false);
        st.assertRoundTrip(validate, "xmlValidate failOnError: false, file: 'a.xml', schema: 'a.xsd'");
    }
}
