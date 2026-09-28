package io.jenkins.plugins.xmleditor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PluginLoadTest {

    @Test
    void pluginIsLoaded(JenkinsRule j) {
        assertNotNull(j.jenkins.getPlugin("xml-editor"));
    }
}
