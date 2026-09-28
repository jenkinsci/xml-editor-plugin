package io.jenkins.plugins.xmleditor.core.xpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class XPathSyntaxTest {

    @Test
    void validExpressionsHaveNoError() {
        assertEquals(Optional.empty(), XPathSyntax.check("/project/version"));
        assertEquals(Optional.empty(), XPathSyntax.check("//dependency[artifactId='x']/@id"));
        assertEquals(Optional.empty(), XPathSyntax.check("count(//m:dependency)"), "prefixes are not checked");
    }

    @Test
    void syntaxErrorsAreDescribed() {
        Optional<String> error = XPathSyntax.check("//[");
        assertTrue(error.isPresent());
        assertTrue(error.get().length() > 5, error.get());
        assertTrue(XPathSyntax.check("/a/b[").isPresent());
    }
}
