package io.jenkins.plugins.xmleditor.steps;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import io.jenkins.plugins.xmleditor.core.model.LDocument;
import java.io.Serializable;

/** Read-only work on a parsed document; runs on the agent that holds the file (or on the controller for text). */
interface XmlTask extends Serializable {

    XmlTaskResult run(LDocument doc) throws XmlEditorException;
}
