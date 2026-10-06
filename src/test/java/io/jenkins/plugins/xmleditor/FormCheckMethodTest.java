package io.jenkins.plugins.xmleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.verb.POST;

/**
 * A {@code doCheck} method annotated with {@code @POST} is only reachable with POST, but forms call it with GET unless
 * the field declares {@code checkMethod="post"}: without it the check silently never shows in the UI.
 */
class FormCheckMethodTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Pattern FIELD = Pattern.compile(
            "<f:entry[^>]*\\bfield=\"(\\w+)\"[^>]*>\\s*<f:(?:textbox|textarea)\\b([^>]*)>", Pattern.DOTALL);

    @Test
    void fieldsWithPostChecksAreCheckedWithPost() throws Exception {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path jelly : files.filter(p -> p.getFileName().toString().equals("config.jelly"))
                    .toList()) {
                Class<?> descriptor = descriptorOf(jelly);
                Matcher m = FIELD.matcher(Files.readString(jelly));
                while (m.find()) {
                    String field = m.group(1);
                    Method check = findCheck(descriptor, field);
                    if (check == null || !check.isAnnotationPresent(POST.class)) {
                        continue;
                    }
                    checked++;
                    if (!m.group(2).contains("checkMethod=\"post\"")) {
                        missing.add(RESOURCES.relativize(jelly) + ": " + field);
                    }
                }
            }
        }
        assertTrue(checked >= 19, "expected the checks of file, files, text and xpath fields, found " + checked);
        assertEquals(List.of(), missing, "fields without checkMethod=\"post\"");
    }

    private static Class<?> descriptorOf(Path jelly) throws ClassNotFoundException {
        String className = RESOURCES
                .relativize(jelly.getParent())
                .toString()
                .replace('\\', '.')
                .replace('/', '.');
        return Class.forName(className + "$DescriptorImpl");
    }

    private static Method findCheck(Class<?> descriptor, String field) {
        String name = "doCheck" + field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1);
        for (Method method : descriptor.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}
