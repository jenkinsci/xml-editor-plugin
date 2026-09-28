package io.jenkins.plugins.xmleditor.core.model;

/** The {@code <?xml ...?>} declaration, kept verbatim. */
public final class LXmlDeclaration extends LRawNode {

    public LXmlDeclaration(String raw) {
        super(raw);
    }
}
