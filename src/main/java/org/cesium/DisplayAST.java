// if you're reading this... printing this tree was the hardest part of this assignment...

package org.cesium;

import org.cesium.ASTNode.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides functionality for printing the Abstarct Syntax Tree (AST)
 */
public class DisplayAST {

    private StringBuilder astString = new StringBuilder();

    private static class TreeNode {
        String label;
        ASTNode node;

        TreeNode(String label, ASTNode node) {
            this.label = label;
            this.node = node;
        }
    }

    public String toString(ASTNode node) {
        visit(node, "", true);
        return astString.toString();
    }

    /**
     * Recursively visits each node in the AST and appends the node to the output.
     * Note that we are casting a node to be the type for that specific ASTNode.
     * then we can access that node's methods.
     */
    private void visit(ASTNode node, String prefix, boolean isLast) {
        if (node instanceof CesiumProgram) {
            processCesiumProgram((CesiumProgram) node, prefix, isLast);
        }
        else if  (node instanceof DeclarationStatement) {
            processDeclarationStatement((DeclarationStatement) node, prefix, isLast);
        }
        else if (node instanceof AssignmentStatement) {
            processAssignmentStatement((AssignmentStatement) node, prefix, isLast);
        }
        else if (node instanceof IfStatement) {
            processIfStatement((IfStatement) node, prefix, isLast);
        }
        else if (node instanceof ForStatement) {
            processForStatement((ForStatement) node, prefix, isLast);
        }
        else if (node instanceof WhileStatement) {
            processWhileStatement((WhileStatement) node, prefix, isLast);
        }
        else if (node instanceof CodeBlock) {
            processCodeBlock((CodeBlock) node, prefix, isLast);
        }
        else if (node instanceof VariableDeclaration) {
            processVariableDeclaration((VariableDeclaration) node, prefix, isLast);
        }
        else if (node instanceof FunctionDeclaration) {
            processFunctionDeclaration((FunctionDeclaration) node, prefix, isLast);
        }
        else if (node instanceof ExpressionStatement) {
            processExpressionStatement((ExpressionStatement) node, prefix, isLast);
        }
        else if (node instanceof ReturnStatement) {
            processReturnStatement((ReturnStatement) node, prefix, isLast);
        }
        else if (node instanceof BinaryExpression) {
            processBinaryExpression((BinaryExpression) node, prefix, isLast);
        }
        else if  (node instanceof UnaryExpression) {
            processUnaryExpression((UnaryExpression) node, prefix, isLast);
        }
        else if (node instanceof VariableExpression) {
            processVariableExpression((VariableExpression) node, prefix, isLast);
        }
        else if  (node instanceof LiteralExpression) {
            processLiteralExpression((LiteralExpression) node, prefix, isLast);
        }
        else if (node instanceof FunctionCallExpression) {
            processFunctionCallExpression((FunctionCallExpression) node, prefix, isLast);
        }
        else if (node instanceof PrintStatement) {
            processPrintStatement((PrintStatement) node, prefix, isLast);
        }
        else {
            astString.append(prefix)
              .append(isLast ? "└── " : "├── ")
              .append("Unknown node type: ")
              .append(node.getClass().getSimpleName())
              .append("\n");
        }
    }

    // handles the formatting of output. and recursively handles children.
    private void processNode(String label, List<TreeNode> nodes, String prefix, boolean isLast) {
        astString.append(prefix)
          .append(isLast ? "└── " : "├── ")
          .append(label)
          .append("\n");

        String nodePrefix = prefix + (isLast ? "    " : "│   ");

        for (int i = 0; i < nodes.size(); i++) {
            TreeNode cesiumNode = nodes.get(i);
            boolean isNodeLast = (i == nodes.size() - 1);

            if (cesiumNode.node != null) {
                visit(cesiumNode.node, nodePrefix, isNodeLast);
            } else {
                astString.append(nodePrefix)
                  .append(isNodeLast ? "└── " : "├── ")
                  .append(cesiumNode.label)
                  .append("\n");
            }
        }
    }


    private void processCesiumProgram(CesiumProgram code, String prefix, boolean isLast) {
        List<TreeNode> cesiumNode = new ArrayList<>();
        for (Statement statement : code.getStatements()) {
            cesiumNode.add(new TreeNode(null, statement));
        }
        processNode("CESIUM_PROGRAM", cesiumNode, prefix, isLast);
    }

    private void processDeclarationStatement(DeclarationStatement declStmt, String prefix, boolean isLast) {
        visit(declStmt.getDeclaration(), prefix, isLast);
    }

    private void processFunctionDeclaration(FunctionDeclaration functionDeclaration, String prefix, boolean isLast) {
        List<TreeNode> funcDeclarationNode = new ArrayList<>();

        funcDeclarationNode.add(new TreeNode("IDENTIFIER (" +
          functionDeclaration.getIdentifier() + ")", null));

        if (!functionDeclaration.getParameters().isEmpty()) {
            List<TreeNode> params = new ArrayList<>();
            for (Parameter param : functionDeclaration.getParameters()) {
                params.add(new TreeNode("PARAMETER: TYPE (" + param.getType()
                  + "), IDENTIFIER (" + param.getIdentifier() + ")", null));
            }
            funcDeclarationNode.add(new TreeNode("PARAMETERS", null));
            funcDeclarationNode.addAll(params);
        }

        funcDeclarationNode.add(new TreeNode(null, functionDeclaration.getBody()));
        processNode("FUNCTION_DECLARATION", funcDeclarationNode, prefix, isLast);
    }

    private void processVariableDeclaration(VariableDeclaration variableDeclaration,
                                            String prefix, boolean isLast) {

        List<TreeNode> variableDecNode = new ArrayList<>();

        variableDecNode.add( new TreeNode("TYPE (" + variableDeclaration.getType() + ")", null));
        variableDecNode.add(new TreeNode("IDENTIFIER (" + variableDeclaration.getIdentifier() + ")", null));

        if (variableDeclaration.getInitializer() != null) {
            variableDecNode.add(new TreeNode("=", null));
            variableDecNode.add(new TreeNode(null, variableDeclaration.getInitializer()));
        }
        processNode("VARIABLE_DECLARATION", variableDecNode, prefix,  isLast);
    }

    private void processAssignmentStatement(AssignmentStatement assignmentStatement,
                                            String prefix, boolean isLast) {

        List<TreeNode> assignmentNode = new ArrayList<>();

        assignmentNode.add(new TreeNode("IDENTIFIER (" + assignmentStatement.getIdentifier() + ")", null));
        assignmentNode.add(new TreeNode("=", null));
        assignmentNode.add(new TreeNode(null, assignmentStatement.getExpression()));

        processNode("ASSIGNMENT_STATEMENT", assignmentNode, prefix, isLast);
    }

    private void processExpressionStatement(ExpressionStatement expressionStatement, String prefix, boolean isLast) {
        visit(expressionStatement.getExpression(), prefix, isLast);
    }

    private void processIfStatement(IfStatement ifStatement, String prefix, boolean isLast) {

        List<TreeNode> ifNode = new ArrayList<>();

        ifNode.add(new TreeNode("CONDITION", ifStatement.getCondition()));
        ifNode.add(new TreeNode("THEN_BLOCK", ifStatement.getThenBlock()));

        if (ifStatement.getElseBlock() != null) {
            ifNode.add(new TreeNode("ELSE_BLOCK", ifStatement.getElseBlock()));
        }

        processNode("IF_STATEMENT", ifNode, prefix, isLast);
    }

    private void processReturnStatement(ReturnStatement returnStatement, String prefix, boolean isLast) {

        List<TreeNode> returnNode = new ArrayList<>();

        returnNode.add(new TreeNode(null, returnStatement.getExpression()));

        processNode("RETURN_STATEMENT", returnNode, prefix, isLast);
    }


    private void processForStatement(ForStatement forStatement, String prefix, boolean isLast) {
        List<TreeNode> forNode = new ArrayList<>();

        if (forStatement.getInitialization() != null) {
            forNode.add(new TreeNode("INITIALIZATION", forStatement.getInitialization()));
        }
        if (forStatement.getCondition() != null) {
            forNode.add(new TreeNode("CONDITION", forStatement.getCondition()));
        }
        if (forStatement.getUpdate() != null) {
            forNode.add(new TreeNode("UPDATE", forStatement.getUpdate()));
        }
        forNode.add(new TreeNode("BODY", forStatement.getBody()));

        processNode("FOR_STATEMENT", forNode, prefix, isLast);
    }

    private void processCodeBlock(CodeBlock codeBlock, String prefix, boolean isLast) {

        List<TreeNode> codeBlockNode = new ArrayList<>();

        for (Statement statement : codeBlock.getStatements()) {
            codeBlockNode.add(new TreeNode(null, statement));
        }

        processNode("CODE_BLOCK", codeBlockNode, prefix, isLast);
    }

    private void processWhileStatement(WhileStatement whileStatement, String prefix, boolean isLast) {
        List<TreeNode> whileNode = new ArrayList<>();

        whileNode.add(new TreeNode("CONDITION", whileStatement.getCondition()));
        whileNode.add(new TreeNode("BODY", whileStatement.getBody()));

        processNode("WHILE_STATEMENT", whileNode, prefix, isLast);
    }



    private void processBinaryExpression(BinaryExpression binaryExpression, String prefix, boolean isLast) {
        List<TreeNode> binaryNode = new ArrayList<>();

        binaryNode.add(new TreeNode(null, binaryExpression.getLeft()));
        binaryNode.add(new TreeNode(null, binaryExpression.getRight()));

        processNode("BINARY_EXPRESSION (" + binaryExpression.getOperator() + ")", binaryNode, prefix, isLast);
    }

    private void processUnaryExpression(UnaryExpression unaryExpression, String  prefix, boolean isLast) {
        List<TreeNode> unaryNode = new ArrayList<>();

        unaryNode.add(new TreeNode(null, unaryExpression.getExpression()));

        processNode("UNARY_EXPRESSION (" + unaryExpression.getOperator() + ")", unaryNode, prefix, isLast);
    }

    private void processVariableExpression(VariableExpression variableExpression, String prefix, boolean isLast) {
        processNode("VARIABLE_EXPRESSION (" + variableExpression.getIdentifier() + ")",
          new ArrayList<>(), prefix, isLast);
    }

    private void processLiteralExpression(LiteralExpression literalExpression, String prefix, boolean isLast) {
        processNode("LITERAL_EXPRESSION (" + literalExpression.getLiteral().getValue() + ")",
          new ArrayList<>(), prefix, isLast);
    }

    private void processPrintStatement(PrintStatement printStatement, String prefix,  boolean isLast) {

        List<TreeNode> printNode = new ArrayList<>();

        printNode.add(new TreeNode(null, printStatement.getExpression()));
        processNode("PRINT_STATEMENT", printNode, prefix, isLast);
    }


    private void processFunctionCallExpression(FunctionCallExpression functionCallExpression,
                                               String prefix, boolean isLast) {

        List<TreeNode> funcCallNode = new ArrayList<>();

        for (Expression arg : functionCallExpression.getArguments()) {
            funcCallNode.add(new TreeNode(null, arg));
        }

        processNode("FUNCTION_CALL (" + functionCallExpression.getIdentifier()
          + ")", funcCallNode, prefix, isLast);
    }
}


