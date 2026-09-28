package io.jenkins.plugins.xmleditor.core.parse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityTest {

    private static final String SECRET = "TOP-SECRET-CONTENT-4711";
    private static final long MAX = 50L * 1024 * 1024;

    @TempDir
    Path tmp;

    private String secretFileUri() throws Exception {
        Path secret = tmp.resolve("secret.txt");
        Files.writeString(secret, SECRET);
        return secret.toUri().toString();
    }

    private static LDocument parse(String xml) throws XmlEditorException {
        return XmlDocuments.parse(xml.getBytes(StandardCharsets.UTF_8), "test.xml", MAX);
    }

    /** Parses and returns everything a user could ever see: the serialized tree or the error message. */
    private static String visibleOutcome(String xml) {
        try {
            LDocument doc = parse(xml);
            return doc.serialize() + "|"
                    + doc.root().children().stream()
                            .map(n -> n instanceof io.jenkins.plugins.xmleditor.core.model.LText t ? t.value() : "")
                            .reduce("", String::concat);
        } catch (XmlEditorException e) {
            return e.getMessage();
        }
    }

    @Test
    void externalGeneralEntityIsNeverResolved() throws Exception {
        String xml = "<!DOCTYPE r [<!ENTITY x SYSTEM \"" + secretFileUri() + "\">]><r>&x;</r>";
        assertFalse(visibleOutcome(xml).contains(SECRET));
    }

    @Test
    void externalParameterEntityIsNeverResolved() throws Exception {
        String xml = "<!DOCTYPE r [<!ENTITY % p SYSTEM \"" + secretFileUri() + "\"> %p;]><r/>";
        assertFalse(visibleOutcome(xml).contains(SECRET));
    }

    @Test
    void externalDtdIsNotLoaded() throws Exception {
        String xml = "<!DOCTYPE r SYSTEM \"" + secretFileUri() + "\"><r/>";
        assertFalse(visibleOutcome(xml).contains(SECRET));
    }

    @Test
    void networkEntitiesAreNotFetched() {
        String xml = "<!DOCTYPE r [<!ENTITY x SYSTEM \"http://10.255.255.1:81/x\">]><r>&x;</r>";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> visibleOutcome(xml));
    }

    @Test
    void billionLaughsIsStopped() {
        StringBuilder sb = new StringBuilder("<!DOCTYPE lolz [<!ENTITY lol0 \"lol\">");
        for (int i = 1; i <= 10; i++) {
            sb.append("<!ENTITY lol").append(i).append(" \"");
            sb.append(("&lol" + (i - 1) + ";").repeat(10));
            sb.append("\">");
        }
        sb.append("]><lolz>&lol10;</lolz>");
        String xml = sb.toString();
        XmlEditorException e = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> assertThrows(XmlEditorException.class, () -> parse(xml)));
        assertTrue(e.getMessage().startsWith("test.xml:"), e.getMessage());
    }

    @Test
    void internalDoctypeIsAcceptedAndKept() throws Exception {
        String xml = "<!DOCTYPE r [<!ENTITY name \"value\">]>\n<r>&name;</r>";
        LDocument doc = parse(xml);
        assertTrue(doc.serialize().equals(xml));
    }
}
