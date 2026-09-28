package io.jenkins.plugins.xmleditor.core.edit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one operation did.
 *
 * @param matched nodes selected by the XPath
 * @param modified nodes actually changed
 * @param oldValues value of each processed node before the operation ({@code null} for a missing attribute); may
 *     contain {@code null}
 */
public record OperationReport(String type, String xpath, int matched, int modified, List<String> oldValues)
        implements Serializable {

    public OperationReport {
        oldValues = Collections.unmodifiableList(new ArrayList<>(oldValues));
    }
}
