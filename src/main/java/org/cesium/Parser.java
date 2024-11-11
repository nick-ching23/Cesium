package org.cesium;

import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int currentTokenIndex;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
    }

    private Token lookAhead() {
        if (currentTokenIndex < tokens.size()) {
            return tokens.get(currentTokenIndex);
        }
        return null;
    }

    private Token nextToken() {
        if (currentTokenIndex < tokens.size()) {
            return tokens.get(currentTokenIndex++);
        }
        return null;
    }

    private void reportError(String message) {
        System.err.println("Syntax Error: " + message + " at token " + lookAhead());
    }

    public ASTNode parseCesiumCode() {
        return parseStatementList();
    }

    private ASTNode parseStatementList() {
        ASTNode node = new ASTNode("StatementList");
        while (lookAhead() != null && lookAhead().getType() != TokenType.UNKNOWN) {
            ASTNode stmt = parseStatement();
            if (stmt != null) {
                node.addChild(stmt);
            } else {
                reportError("Invalid statement");
                skipToDelimiter();
            }
        }
        return node;
    }

    private void skipToDelimiter() {
        while (lookAhead() != null && !isDelimiter(lookAhead())) {
            nextToken();
        }
    }

    private boolean isDelimiter(Token token) {
        return token.getValue().equals(";") || token.getValue().equals("}") || token.getValue().equals(")");
    }

    private ASTNode parseStatement() {
        Token token = lookAhead();
        if (token == null) return null;

        switch (token.getType()) {
            case KEYWORD -> {
                if (token.getValue().equals("if")) return parseIfStatement();
                if (token.getValue().equals("for")) return parseForStatement();
                if (token.getValue().equals("while")) return parseWhileStatement();
                if (token.getValue().equals("return")) return parseReturnStatement();
                if (isType(token.getValue())) return parseVariableDeclaration();
            }
            case IDENTIFIER -> {
                return parseAssignmentStatement();
            }
            default -> {
                reportError("Unexpected token: " + token.getValue());
                nextToken();
                return null;
            }
        }
        return null;
    }

    private boolean isType(String value) {
        return value.equals("int") || value.equals("float") || value.equals("string") || value.equals("list") || value.equals("Stream") || value.equals("Reactive");
    }

    private ASTNode parseVariableDeclaration() {
        Token type = nextToken();
        Token identifier = nextToken();
        if (!expectToken(TokenType.OPERATOR, "=")) {
            reportError("Expected '=' after variable declaration");
            return null;
        }
        ASTNode expression = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ";")) {
            reportError("Expected ';' after expression");
            return null;
        }
        ASTNode typeNode = new ASTNode("Type", type.getValue());
        ASTNode identifierNode = new ASTNode("Identifier", identifier.getValue());
        return new ASTNode("VariableDeclaration", typeNode, identifierNode, expression);

    }

    private ASTNode parseAssignmentStatement() {
        Token identifier = nextToken();
        if (!expectToken(TokenType.OPERATOR, "=")) {
            reportError("Expected '=' after identifier");
            return null;
        }
        ASTNode expression = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ";")) {
            reportError("Expected ';' after expression");
            return null;
        }
        ASTNode identifierNode = new ASTNode("Identifier", identifier.getValue());
        return new ASTNode("Assignment", identifierNode, expression);

    }

    private ASTNode parseReturnStatement() {
        nextToken(); // Consume 'return'
        ASTNode expression = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ";")) {
            reportError("Expected ';' after return expression");
            return null;
        }
        return new ASTNode("ReturnStatement", expression);
    }

    // Parse an if statement
    private ASTNode parseIfStatement() {
        nextToken(); // Consume 'if'
        if (!expectToken(TokenType.DELIMITER, "(")) {
            reportError("Expected '(' after 'if'");
            return null;
        }
        ASTNode condition = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ")")) {
            reportError("Expected ')' after condition");
            return null;
        }
        ASTNode thenBlock = parseCodeBlock();
        ASTNode elseBlock = null;
        if (lookAhead() != null && lookAhead().getValue().equals("else")) {
            nextToken(); // Consume 'else'
            elseBlock = parseCodeBlock();
        }
        return new ASTNode("IfStatement", condition, thenBlock, elseBlock);
    }

    // Parse for statement
    private ASTNode parseForStatement() {
        nextToken(); // Consume 'for'
        if (!expectToken(TokenType.DELIMITER, "(")) {
            reportError("Expected '(' after 'for'");
            return null;
        }
        ASTNode init = parseInitFor();
        if (!expectToken(TokenType.DELIMITER, ";")) {
            reportError("Expected ';' after initialization");
            return null;
        }
        ASTNode condition = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ";")) {
            reportError("Expected ';' after condition");
            return null;
        }
        ASTNode update = parseAssignmentStatement();
        if (!expectToken(TokenType.DELIMITER, ")")) {
            reportError("Expected ')' after update");
            return null;
        }
        ASTNode body = parseCodeBlock();
        return new ASTNode("ForStatement", init, condition, update, body);
    }

    // Parse a while statement
    private ASTNode parseWhileStatement() {
        nextToken(); // Consume 'while'
        if (!expectToken(TokenType.DELIMITER, "(")) {
            reportError("Expected '(' after 'while'");
            return null;
        }
        ASTNode condition = parseExpression();
        if (!expectToken(TokenType.DELIMITER, ")")) {
            reportError("Expected ')' after condition");
            return null;
        }
        ASTNode body = parseCodeBlock();
        return new ASTNode("WhileStatement", condition, body);
    }

    private ASTNode parseExpression() {
        return parsePrimaryExpression();
    }

    private ASTNode parsePrimaryExpression() {
        Token token = lookAhead();
        if (token == null) return null;

        switch (token.getType()) {
            case IDENTIFIER -> {
                return new ASTNode("Identifier", nextToken().getValue());
            }
            case NUMERIC_LITERAL -> {
                return new ASTNode("NumericLiteral", nextToken().getValue());
            }
            case STRING_LITERAL -> {
                return new ASTNode("StringLiteral", nextToken().getValue());
            }
            case BOOLEAN_LITERAL -> {
                return new ASTNode("BooleanLiteral", nextToken().getValue());
            }
            case DELIMITER -> {
                if (token.getValue().equals("(")) {
                    nextToken(); // Consume '('
                    ASTNode expr = parseExpression();
                    if (!expectToken(TokenType.DELIMITER, ")")) {
                        reportError("Expected ')' after expression");
                        return null;
                    }
                    return expr;
                }
            }
            default -> {
                reportError("Unexpected primary expression: " + token.getValue());
                nextToken();
            }
        }
        return null;
    }

    private ASTNode parseInitFor() {
        Token token = lookAhead();
        if (token != null && isType(token.getValue())) {
            return parseVariableDeclaration();
        } else if (token != null && token.getType() == TokenType.IDENTIFIER) {
            return parseAssignmentStatement();
        }
        return null; // Epsilon
    }
    // Parses code block
    private ASTNode parseCodeBlock() {
        if (!expectToken(TokenType.DELIMITER, "{")) {
            reportError("Expected '{' at the start of code block");
            return null;
        }
        ASTNode block = parseStatementList();
        if (!expectToken(TokenType.DELIMITER, "}")) {
            reportError("Expected '}' at the end of code block");
            return null;
        }
        return new ASTNode("CodeBlock", block);
    }

    private boolean expectToken(TokenType type, String value) {
        Token token = nextToken();
        return token != null && token.getType() == type && token.getValue().equals(value);
    }
}
