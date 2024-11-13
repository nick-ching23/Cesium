package org.cesium;

import java.util.List;

/**
 * Abstract base class for all nodes in the Abstract Syntax Tree (AST).
 * We create classes that represent each "node" in the AST where a node
 * represents a piece of Cesium code (like a declaration, return or expression)
 */

public abstract class ASTNode {

    // ALL NODES THAT ARE STATEMENTS

    /**
     * Abstract base class for all statement nodes.
     */
    public static abstract class Statement extends ASTNode {}

    public static class DeclarationStatement extends Statement {
        private final Declaration declaration;

        public DeclarationStatement(Declaration declaration) {
            this.declaration = declaration;
        }

        public Declaration getDeclaration() {
            return declaration;
        }

        @Override
        public String toString() {
            return "DeclarationStatement: " + declaration;
        }
    }

    public static class AssignmentStatement extends Statement {
        private String identifier;
        private Expression expression;

        public AssignmentStatement(String identifier, Expression expression) {
            this.identifier = identifier;
            this.expression = expression;
        }

        public String getIdentifier() {
            return identifier;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "AssignmentStatement: " + identifier + " = " + expression;
        }
    }

    public static class ReturnStatement extends Statement {
        private Expression expression;

        public ReturnStatement(Expression  expression) {
            this.expression = expression;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "ReturnStatement: " + expression;
        }
    }



    public static class ExpressionStatement extends Statement {
        private Expression expression;

        public ExpressionStatement(Expression expression) {
            this.expression = expression;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "ExpressionStatement: " + expression;
        }
    }


    public static class IfStatement extends Statement {
        private Expression condition;
        private CodeBlock thenBlock;
        private CodeBlock elseBlock; // Can be null

        public IfStatement(Expression condition, CodeBlock thenBlock, CodeBlock elseBlock) {
            this.condition = condition;
            this.thenBlock = thenBlock;
            this.elseBlock = elseBlock;
        }

        public Expression getCondition() {
            return condition;
        }

        public CodeBlock getThenBlock() {
            return thenBlock;
        }

        public CodeBlock getElseBlock() {
            return elseBlock;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("IfStatement:\n");
            builder.append("Condition: ").append(condition).append("\n");
            builder.append("Then: ").append(thenBlock).append("\n");
            if (elseBlock != null) {
                builder.append("Else: ").append(elseBlock).append("\n");
            }
            return builder.toString();
        }
    }


    public static class ForStatement extends Statement {
        private Statement initialization; // Can be null
        private Expression condition;     // Can be null
        private Statement update;         // Can be null
        private CodeBlock body;

        public ForStatement(Statement initialization, Expression condition, Statement update, CodeBlock body) {
            this.initialization = initialization;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }

        public Statement getInitialization() {
            return initialization;
        }

        public Expression getCondition() {
            return condition;
        }

        public Statement getUpdate() {
            return update;
        }

        public CodeBlock getBody() {
            return body;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ForStatement:\n");
            if (initialization != null) {
                builder.append("Initialization: ").append(initialization).append("\n");
            }
            if (condition != null) {
                builder.append("Condition: ").append(condition).append("\n");
            }
            if (update != null) {
                builder.append("Update: ").append(update).append("\n");
            }
            builder.append("Body: ").append(body).append("\n");
            return builder.toString();
        }
    }

    public static class WhileStatement extends Statement {
        private Expression condition;
        private CodeBlock body;

        public WhileStatement(Expression condition, CodeBlock body) {
            this.condition = condition;
            this.body = body;
        }

        public Expression getCondition() {
            return condition;
        }

        public CodeBlock getBody() {
            return body;
        }

        @Override
        public String toString() {
            return "WhileStatement:\nCondition: " + condition + "\nBody: " + body;
        }
    }

    public static class PrintStatement extends Statement {
        private Expression expression;

        public PrintStatement(Expression expression) {
            this.expression = expression;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "PrintStatement: print(" + expression + ")";
        }
    }

    public static class CodeBlock extends Statement {
        private List<Statement> statements;

        public CodeBlock(List<Statement> statements) {
            this.statements = statements;
        }

        public List<Statement> getStatements() {
            return statements;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{\n");
            for (Statement stmt : statements) {
                builder.append(stmt.toString()).append("\n");
            }
            builder.append("}");
            return builder.toString();
        }
    }



    // ALL NODES THAT ARE DECLARATIONS


    /**
     * Abstract base class for all declaration nodes.
     */
    public static abstract class Declaration extends ASTNode {}

    public static class VariableDeclaration extends Declaration {
        private String type;
        private String identifier;
        private Expression initializer; // Can be null

        public VariableDeclaration(String type, String identifier, Expression initializer) {
            this.type = type;
            this.identifier = identifier;
            this.initializer = initializer;
        }

        public String getType() {
            return type;
        }

        public String getIdentifier() {
            return identifier;
        }

        public Expression getInitializer() {
            return initializer;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("VariableDeclaration: ").append(type).append(" ").append(identifier);
            if (initializer != null) {
                builder.append(" = ").append(initializer);
            }
            return builder.toString();
        }
    }

    public static class FunctionDeclaration extends Declaration {
        private String identifier;
        private List<Parameter> parameters;
        private CodeBlock body;

        public FunctionDeclaration(String identifier, List<Parameter> parameters, CodeBlock body) {
            this.identifier = identifier;
            this.parameters = parameters;
            this.body = body;
        }

        public String getIdentifier() {
            return identifier;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

        public CodeBlock getBody() {
            return body;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("FunctionDeclaration: ").append(identifier).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                builder.append(parameters.get(i));
                if (i < parameters.size() - 1) {
                    builder.append(", ");
                }
            }
            builder.append(")\n");
            builder.append("Body: ").append(body);
            return builder.toString();
        }
    }

    public static class Parameter extends ASTNode {
        private String type;
        private String identifier;

        public Parameter(String type, String identifier) {
            this.type = type;
            this.identifier = identifier;
        }

        public String getType() {
            return type;
        }

        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String toString() {
            return type + " " + identifier;
        }
    }




    // ALL NODES THAT REPRESENT EXPRESSIONS

    /**
     * Abstract base class for all expression nodes.
     */
    public static abstract class Expression extends ASTNode {}

    public static class BinaryExpression extends Expression {
        private Expression leftExpression;
        private String operator;
        private Expression rightExpression;

        public BinaryExpression(Expression left, String operator, Expression right) {
            this.leftExpression = left;
            this.operator = operator;
            this.rightExpression = right;
        }

        public Expression getLeft() {
            return leftExpression;
        }

        public String getOperator() {
            return operator;
        }

        public Expression getRight() {
            return rightExpression;
        }

        @Override
        public String toString() {
            return "(" + leftExpression + " " + operator + " " + rightExpression + ")";
        }
    }

    public static class UnaryExpression extends Expression  {
        private String operator;
        private Expression expression;

        public UnaryExpression(String operator, Expression expression) {
            this.operator = operator;
            this.expression = expression;
        }

        public String getOperator() {
            return operator;
        }

        public Expression getExpression() {
            return expression;
        }

        @Override
        public String toString() {
            return "(" + operator + expression + ")";
        }
    }

    public static class LiteralExpression extends Expression {
        private Token literal;

        public LiteralExpression(Token literal) {
            this.literal = literal;
        }

        public Token getLiteral() {
            return literal;
        }

        @Override
        public String toString() {
            return literal.getValue();
        }
    }

    public static class VariableExpression extends Expression {
        private String identifier;

        public VariableExpression(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String toString() {
            return identifier;
        }
    }


    public static class FunctionCallExpression extends Expression {
        private String identifier;
        private List<Expression> arguments;

        public FunctionCallExpression(String identifier, List<Expression> arguments) {
            this.identifier = identifier;
            this.arguments = arguments;
        }

        public String getIdentifier() {
            return identifier;
        }

        public List<Expression> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(identifier).append("(");
            for (int i = 0; i < arguments.size(); i++) {
                builder.append(arguments.get(i));
                if (i < arguments.size() - 1) {
                    builder.append(", ");
                }
            }
            builder.append(")");
            return builder.toString();
        }
    }

    /**
     * CesiumCode object represents the root of our abstract syntax tree.
     * This is what we will ultimately return to visualize the AST.
     */
    public static class CesiumProgram extends ASTNode {
        private List<Statement> parsedStatements;

        public CesiumProgram(List<Statement> statements) {
            this.parsedStatements = statements;
        }

        public List<Statement> getStatements() {
            return parsedStatements;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("CesiumCode:\n");
            for (Statement statement : parsedStatements) {
                builder.append(statement).append("\n");
            }
            return builder.toString();
        }
    }
}
