package io.jenkins.plugins.xmleditor.steps;

import java.io.Serializable;
import java.util.List;

/**
 * The value returned to the Pipeline plus messages to print in the build log.
 *
 * @param value only String, Double, Boolean, ArrayList, LinkedHashMap or null
 */
record XmlTaskResult(Serializable value, List<String> messages) implements Serializable {

    XmlTaskResult {
        messages = List.copyOf(messages);
    }
}
