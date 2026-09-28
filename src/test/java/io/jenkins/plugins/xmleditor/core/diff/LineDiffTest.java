package io.jenkins.plugins.xmleditor.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LineDiffTest {

    @Test
    void identicalTextsGiveNoDiff() {
        assertEquals("", LineDiff.unified("a\nb\n", "a\nb\n", "x.xml", 3, 200));
    }

    @Test
    void changeInTheMiddleWithContext() {
        String before = "1\n2\n3\n4\n5\n6\n7\n8\n9\n";
        String after = "1\n2\n3\n4\nFIVE\n6\n7\n8\n9\n";
        String expected = """
                --- a/x.xml
                +++ b/x.xml
                @@ -2,7 +2,7 @@
                 2
                 3
                 4
                -5
                +FIVE
                 6
                 7
                 8
                """;
        assertEquals(expected, LineDiff.unified(before, after, "x.xml", 3, 200));
    }

    @Test
    void distantChangesGetSeparateHunksAndAdditions() {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            b.append(i).append('\n');
        }
        String before = b.toString();
        String after = before.replace("\n2\n", "\ntwo\n").replace("\n19\n", "\n19\nnew\n");
        String diff = LineDiff.unified(before, after, "x.xml", 1, 200);
        assertTrue(diff.contains("@@ -1,3 +1,3 @@\n 1\n-2\n+two\n 3\n"), diff);
        assertTrue(diff.contains("@@ -19,2 +19,3 @@\n 19\n+new\n 20\n"), diff);
    }

    @Test
    void crlfIsNotShownAsAChange() {
        String diff = LineDiff.unified("a\r\nb\r\n", "a\r\nc\r\n", "x.xml", 3, 200);
        assertFalse(diff.contains("\r"));
        assertTrue(diff.contains("-b\n+c\n"), diff);
    }

    @Test
    void longDiffsAreTruncated() {
        String before = "a\n".repeat(500);
        String after = "b\n".repeat(500);
        String diff = LineDiff.unified(before, after, "x.xml", 3, 50);
        assertEquals(51, diff.split("\n").length);
        assertTrue(diff.endsWith("... (diff truncated)\n"), diff);
    }

    @Test
    void largeFilesWithALocalChangeAreFast() {
        String before = "<line/>\n".repeat(200_000);
        String after = before.substring(0, 800_000) + "<changed/>\n" + before.substring(800_000);
        String diff = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> LineDiff.unified(before, after, "big.xml", 3, 200));
        assertTrue(diff.contains("+<changed/>"), diff);
    }
}
