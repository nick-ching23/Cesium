package org.cesium;

import java.util.ArrayList;
import java.util.List;

public class ASTNode {
    private final String type;
    private final String value;
    private final List<ASTNode> children;

    // Constructor for nodes with type and value
    public ASTNode(String type, String value) {
        this.type = type;
        this.value = value;
        this.children = new ArrayList<>();
    }

    // Constructor for nodes with type and children
    public ASTNode(String type, ASTNode... children) {
        this.type = type;
        this.value = null;
        this.children = new ArrayList<>();
        for (ASTNode child : children) {
            if (child != null) {
                this.children.add(child);
            }
        }
    }

    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public List<ASTNode> getChildren() {
        return children;
    }

    public void print(String indent, boolean isLast) {
        String branch = isLast ? "└── " : "├── ";
        System.out.println(indent + branch + type + (value != null ? " (" + value + ")" : ""));
        String childIndent = indent + (isLast ? "    " : "│   ");
        for (int i = 0; i < children.size(); i++) {
            children.get(i).print(childIndent, i == children.size() - 1);
        }
    }
}
