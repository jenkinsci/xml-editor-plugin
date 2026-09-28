package io.jenkins.plugins.xmleditor.core.edit;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.util.Locale;

/** How many nodes an operation's XPath must match: {@code AT_LEAST_ONE} (default), {@code ONE}, {@code ANY} or n. */
public final class Expectation {

    private static final int AT_LEAST_ONE = -1;
    private static final int ANY = -2;

    private final int count;

    private Expectation(int count) {
        this.count = count;
    }

    public static Expectation parse(String value) throws XmlEditorException {
        if (value == null || value.isBlank()) {
            return new Expectation(AT_LEAST_ONE);
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        switch (v) {
            case "AT_LEAST_ONE":
                return new Expectation(AT_LEAST_ONE);
            case "ANY":
                return new Expectation(ANY);
            case "ONE":
                return new Expectation(1);
            default:
                if (v.matches("\\d{1,9}")) {
                    return new Expectation(Integer.parseInt(v));
                }
                throw new XmlEditorException(
                        "Invalid expected value '" + value + "': use ONE, AT_LEAST_ONE, ANY or a number such as '2'");
        }
    }

    /** @param operation description such as {@code Operation 2 (setText)} */
    public void check(int matched, String operation, String xpath) throws XmlEditorException {
        boolean ok = count == ANY || (count == AT_LEAST_ONE ? matched > 0 : matched == count);
        if (!ok) {
            String hint = count == AT_LEAST_ONE ? " (use expected: 'ANY' to allow no match)" : "";
            throw new XmlEditorException(operation + ": XPath '" + xpath + "' matched " + matched
                    + " node(s), expected " + describe() + hint);
        }
    }

    private String describe() {
        return switch (count) {
            case AT_LEAST_ONE -> "at least one";
            case ANY -> "any number";
            default -> "exactly " + count;
        };
    }
}
