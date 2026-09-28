package io.jenkins.plugins.xmleditor.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A node with children: the document or an element. */
public abstract class LParent extends LNode {

    private final List<LNode> children = new ArrayList<>();

    public List<LNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** Position of {@code child} (compared by identity), or -1. */
    public int indexOf(LNode child) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == child) {
                return i;
            }
        }
        return -1;
    }

    public void insert(int index, LNode child) {
        if (child.parent != null) {
            child.parent.remove(child);
        }
        child.parent = this;
        children.add(index, child);
    }

    public void append(LNode child) {
        insert(children.size(), child);
    }

    public void remove(LNode child) {
        int i = indexOf(child);
        if (i >= 0) {
            children.remove(i);
            child.parent = null;
        }
    }

    protected void writeChildren(StringBuilder out) {
        for (LNode child : children) {
            child.write(out);
        }
    }
}
