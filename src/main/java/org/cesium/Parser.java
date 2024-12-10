package org.cesium;

import org.cesium.ASTNode.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Parser class is responsible for converting a list of tokens into an Abstract Syntax Tree (AST)
 * based on the Context Free Grammar for Cesium.  This grammar is defined in the README.
 * Please note that for each parsed statement we are returning a child of ASTNode. This is what will
 * allow us to visualize the parsed code.
 */
public class Parser {

    private final List<Token> tokens; // list of tokens from scanner
    private final int offset = 1; // lookahead set to 1
    private Token currentToken; // the current token we are processing
    private int tokenIndex; // the current index within the token list

    /**
     * Constructor: initializes a Parser object with a list of tokens.
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.tokenIndex = 0;
        this.currentToken = tokens.isEmpty() ? null : tokens.get(0);
    }

    /**
     * Entry point for the parser class. Use this function
     * to actually parse tokens.
     */
    public CesiumProgram parse() throws ParseException {
        List<Statement> parsedCode = parseStatementList();
        return new CesiumProgram(parsedCode);
    }

    /**
     * Advances to the next token in the list
     */
    private void advanceToken() {
        tokenIndex++;

        if (tokenIndex < tokens.size()) {
            currentToken = tokens.get(tokenIndex);
        } else {
            currentToken = null;
        }
    }


    /**
     * we essentially check if the current token matches what is expected, if yes
     * then we consume it.
     */
    private boolean checkAndConsume(TokenType type, String lexeme) {

        if (currentToken != null && currentToken.getType() == type && currentToken.getValue().equals(lexeme)) {
            advanceToken();
            return true;
        }
        return false;
    }

    private Token consumeTokenIfMatches(TokenType type, String lexeme) throws ParseException {
        if (currentToken != null && currentToken.getType() == type && currentToken.getValue().equals(lexeme)) {
            Token matchedToken = currentToken;
            advanceToken();
            return matchedToken;
        }
        throw new ParseException("Expected '" + lexeme + "' but found '"
          + (currentToken != null ? currentToken.getValue() : "EOF") + "'");
    }
    private Token consumeTokenIfMatches(TokenType type) throws ParseException {
        if (currentToken != null && currentToken.getType() == type) {
            Token matchedToken = currentToken;
            advanceToken();
            return matchedToken;
        }
        throw new ParseException("Expected token of type " + type + " but found '"
          + (currentToken != null ? currentToken.getValue() : "EOF") + "'");
    }

    /**
     * Peeks ahead in the token list without consuming tokens.
     */
    private Token lookAhead() {
        int currentIdx = tokenIndex + offset; // recall offset = 1

        if (currentIdx < tokens.size()) {
            return tokens.get(currentIdx);
        } else {
             return null;
        }
    }

    /**
     * Here we will loop through every token. And return a list of parsed statements
     */
    private List<Statement> parseStatementList() throws ParseException {
        List<Statement> parseResults = new ArrayList<>();

        while (currentToken != null && !currentToken.getValue().equals("}")) {
            parseResults.add(parseStatement());
        }

        return parseResults;
    }

    /**
     * Implements the "Statement -> ..." production rule in Cesium grammar.
     * This code is the entry point for each statement in our grammar.
     */
    private Statement parseStatement() throws ParseException {

        if (currentToken == null) {
            throw new ParseException("Unexpected end of input");
        }
        if (checkAndConsume(TokenType.KEYWORD, "if")) {
            return parseIf();
        }
        else if (checkAndConsume(TokenType.KEYWORD, "for")) {
            return parseFor();
        }
        else if (checkAndConsume(TokenType.KEYWORD, "while")) {
            return parseWhile();
        }
        else if (checkAndConsume(TokenType.KEYWORD, "function")) {
            return new DeclarationStatement(parseFunctionDeclaration());
        }
        else if (checkAndConsume(TokenType.KEYWORD, "return")) {
            Expression expression = parseExpression();
            consumeTokenIfMatches(TokenType.DELIMITER, ";");
            return new ReturnStatement(expression);
        }
        else if (checkAndConsume(TokenType.KEYWORD, "print")) {
            return parsePrint();
        }
        else if (checkAndConsume(TokenType.DELIMITER, "{")) {
            return parseCodeBlock();
        }

        else if (isTypeToken(currentToken)) {
            DeclarationStatement declarationStatement = new DeclarationStatement(parseVariableDeclaration());
            consumeTokenIfMatches(TokenType.DELIMITER, ";");
            return declarationStatement;
        }
        else if (currentToken.getType() == TokenType.IDENTIFIER) {
            Token lookahead = lookAhead();
            if (lookahead != null && lookahead.getType() == TokenType.OPERATOR && lookahead.getValue().equals("=")) {
                AssignmentStatement assignmentStatement = parseAssignmentStatement();
                consumeTokenIfMatches(TokenType.DELIMITER, ";");
                return assignmentStatement;
            } else {
                Expression expr = parseExpression();
                consumeTokenIfMatches(TokenType.DELIMITER, ";");
                return new ExpressionStatement(expr);
            }
        }
        else {
            // if we reach this point, the token isn't supposed to be there.
            throw new ParseException("Unexpected token: " + currentToken.getValue());
        }
    }

    // PARSING RELATED TO DECLARATION (variable and function declaration)

    /**
     * Handles variable declaration. we have two cases, both are handled:
     * e.g. int a;
     * e.g. int a = 2;
     */
    private VariableDeclaration parseVariableDeclaration() throws ParseException {
        // the LHS
        String type = parseType();
        Token identifierToken = consumeTokenIfMatches(TokenType.IDENTIFIER);
        String identifier = identifierToken.getValue();

        // check the RHS
        Expression assignedExpression = null;
        if (checkAndConsume(TokenType.OPERATOR, "=")) {
            assignedExpression = parseExpression();
        }

        return new VariableDeclaration(type, identifier, assignedExpression);
    }

    /**
     * Handles Function declaration. we can have two cases, both are handled:
     * e.g. function func1(){}
     * e.g. function func2(<type> Param1, <type> Param2,...){}
     */
    private FunctionDeclaration parseFunctionDeclaration() throws ParseException {
        debugPrint("Entering parseFunctionDeclaration, current token: " + currentToken);

        // function name
        Token identifierToken = consumeTokenIfMatches(TokenType.IDENTIFIER);
        String functionName = identifierToken.getValue();

        // check the parameters
        consumeTokenIfMatches(TokenType.DELIMITER, "(");

        List<Parameter> parameters = new ArrayList<>();
        if (!checkAndConsume(TokenType.DELIMITER, ")")) {
            parameters = parseParameterList();
            consumeTokenIfMatches(TokenType.DELIMITER, ")");
        }


        // parse the body of the function
        consumeTokenIfMatches(TokenType.DELIMITER, "{");
        CodeBlock bodyCode = parseCodeBlock();

        return new FunctionDeclaration(functionName, parameters, bodyCode);
    }

    /**
     * Assumes that the parameter list actually has elements (checked in parse function declaration)
     */
    private List<Parameter> parseParameterList() throws ParseException {
        List<Parameter> parameters = new ArrayList<>();

        // parse first param, then loop through remainders
        parameters.add(parseParameter());

        while (checkAndConsume(TokenType.DELIMITER, ",")) {
            parameters.add(parseParameter());
        }
        return parameters;
    }

    /**
     * ensures that we are actually getting a type. if we get a type and it's what we want,
     * consume the token and advance. if not, then we throw an error
     */
    private String parseType() throws ParseException {
        if (currentToken != null && currentToken.getType() == TokenType.KEYWORD
          && isTypeKeyword(currentToken.getValue())) {
            String type = currentToken.getValue();
            advanceToken();
            return type;

        } else {
            String found = currentToken != null ? currentToken.getValue() : "EOF";
            throw new ParseException("Expected type but found '" + found + "'");
        }
    }

    /**
     * Our parameter syntax follows <type> IDENTIFIER. So we first parse type,
     * then identifier.
     */
    private Parameter parseParameter() throws ParseException {
        String type = parseType();

        Token identifierToken = consumeTokenIfMatches(TokenType.IDENTIFIER);
        String identifier = identifierToken.getValue();

        return new Parameter(type, identifier);
    }

    /**
     * Checks is a lexeme is a type keyword
     */
    private boolean isTypeKeyword(String lexeme) {
        return lexeme.equals("int") || lexeme.equals("float") || lexeme.equals("string") ||
          lexeme.equals("Stream") || lexeme.equals("Reactive");
    }

    /**
     * ensures that the current token is a type keyword
     */
    private boolean isTypeToken(Token token) {
        return token.getType() == TokenType.KEYWORD && isTypeKeyword(token.getValue());
    }


    // PARSING RELATED TO ASSIGNMENT

    /**
     * Assignment assumes that we follow the pattern:
     * IDENTIFIER = Expression
     * e.g. c = a + b;
     */
    private AssignmentStatement parseAssignmentStatement() throws ParseException {
        // handle identifier
        Token identifierToken = consumeTokenIfMatches(TokenType.IDENTIFIER);
        String variableName = identifierToken.getValue();

        // handle expression
        consumeTokenIfMatches(TokenType.OPERATOR, "=");
        Expression expression = parseExpression();

        return new AssignmentStatement(variableName, expression);
    }


    // PARSING RELATED TO EXPRESSIONS

    /**
     * ParseExpression acts as the entry point for parsing expressions.
     * We follow precedence orders to ensure that operations are executed
     * in the correct order. Highest priority operators are at the lowest
     * level in the call hierarchy.
     *
     * <p> Note here: these will recursively construct the necessary components
     */
    private Expression parseExpression() throws ParseException {
        return parseOrExpression();
    }

    private Expression parseOrExpression() throws ParseException {
        Expression leftExpression = parseAndExpression();

        // we could have a chain of || expressions
        while (checkAndConsume(TokenType.OPERATOR, "||")) {
            String operator = "||";
            Expression rightExpression = parseAndExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }

        return leftExpression;
    }

    private Expression parseAndExpression() throws ParseException {
        Expression leftExpression = parseEqualityExpression();

        // we could have a chain of && expressions
        while (checkAndConsume(TokenType.OPERATOR, "&&")) {
            String operator = "&&";
            Expression rightExpression = parseEqualityExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }
        return leftExpression;
    }

    private Expression parseEqualityExpression() throws ParseException {
        Expression leftExpression = parseRelationalExpression();

        while (currentToken != null && isEqualityOperator(currentToken)) {
            String operator = currentToken.getValue();
            advanceToken();
            Expression rightExpression = parseRelationalExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }
        return leftExpression;
    }

    private Expression parseRelationalExpression() throws ParseException {
        Expression leftExpression = parseAdditiveExpression();

        while (currentToken != null && isRelationalOperator(currentToken)) {
            String operator = currentToken.getValue();
            advanceToken();
            Expression rightExpression = parseAdditiveExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }
        return leftExpression;
    }

    private Expression parseAdditiveExpression() throws ParseException {
        Expression leftExpression = parseMultiplicativeExpression();

        while (currentToken != null && isAdditive(currentToken)) {
            String operator = currentToken.getValue();
            advanceToken();
            Expression rightExpression = parseMultiplicativeExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }
        return leftExpression;
    }

    private Expression parseMultiplicativeExpression() throws ParseException {
        Expression leftExpression = parseUnaryExpression();

        while (currentToken != null && isMultiplicative(currentToken)) {
            String operator = currentToken.getValue();
            advanceToken();
            Expression rightExpression = parseUnaryExpression();
            leftExpression = new BinaryExpression(leftExpression, operator, rightExpression);
        }
        return leftExpression;
    }


    private Expression parseUnaryExpression() throws ParseException {
        if (currentToken != null && isUnary(currentToken)) {
            String operator = currentToken.getValue();
            advanceToken();
            Expression expression = parseUnaryExpression();
            return new UnaryExpression(operator, expression);
        } else {
            return parsePrimaryExpression();
        }
    }

    /**
     * Primary expressions represent the highest priority in expressions.
     * These include: literals, identifiers, func calls or anything in parentheses.
     */
    private Expression parsePrimaryExpression() throws ParseException {

        // check literal
        if (currentToken != null && isLiteral(currentToken)) {
            Token literal = currentToken;
            advanceToken();
            return new LiteralExpression(literal);
        }
        // check parenthesis
        else if (checkAndConsume(TokenType.DELIMITER, "(")) {
            Expression expression = parseExpression();
            consumeTokenIfMatches(TokenType.DELIMITER, ")");
            return expression;
        }
        // check identifier. Note here we could have parameters inside a function call
        // e.g. a = leave();
        else if (currentToken != null && currentToken.getType() == TokenType.IDENTIFIER) {

            Token identifierToken = consumeTokenIfMatches(TokenType.IDENTIFIER);
            String identifier = identifierToken.getValue();

            // consume the brackets and arguments inside the brackets
            if (checkAndConsume(TokenType.DELIMITER, "(")) {
                List<Expression> arguments = new ArrayList<>();

                if (!checkAndConsume(TokenType.DELIMITER, ")")) {
                    arguments = parseArgs();
                    consumeTokenIfMatches(TokenType.DELIMITER, ")");
                }
                return new FunctionCallExpression(identifier, arguments);
            }
            else {
                return new VariableExpression(identifier);
            }
        }
        else {
            String unexpectedFound;
            if (currentToken != null) {
                unexpectedFound = currentToken.getValue();
            } else {
                unexpectedFound = "EOF";
            }
            throw new ParseException("Unexpected token in primary expression: " + unexpectedFound);
        }
    }

    /**
     * Handle parsing of arguments. Note these are different than function signature.
     * we don't have type specifiers in Cesium for arguments:
     * e.g. function multiply(int a, int b) {return a * b; }
     * e.g. multiply(10, 10);
     */
    private List<Expression> parseArgs() throws ParseException {
        List<Expression> arguments = new ArrayList<>();
        arguments.add(parseExpression());

        while (checkAndConsume(TokenType.DELIMITER, ",")) {
            arguments.add(parseExpression());
        }
        return arguments;
    }


    // Helper functions for identifying what kind of expression is being parsed
    private boolean isLiteral(Token currentToken) {
        return currentToken.getType() == TokenType.NUMERIC_LITERAL
          || currentToken.getType() == TokenType.STRING_LITERAL
          || currentToken.getType() == TokenType.BOOLEAN_LITERAL;
    }

    private boolean isEqualityOperator(Token currentToken) {
        return currentToken.getType() == TokenType.OPERATOR &&
          (currentToken.getValue().equals("==") || currentToken.getValue().equals("!="));
    }

    private boolean isRelationalOperator(Token currentToken) {
        return currentToken.getType() == TokenType.OPERATOR &&
          (currentToken.getValue().equals(">")
            || currentToken.getValue().equals("<")
            || currentToken.getValue().equals(">=")
            || currentToken.getValue().equals("<="));
    }

    private boolean isAdditive(Token currentToken) {
        return currentToken.getType() == TokenType.OPERATOR &&
          (currentToken.getValue().equals("+") || currentToken.getValue().equals("-"));
    }

    private boolean isMultiplicative(Token currentToken) {
        return currentToken.getType() == TokenType.OPERATOR &&
          (currentToken.getValue().equals("*") || currentToken.getValue().equals("/"));
    }

    private boolean isUnary(Token currentToken) {
        return currentToken.getType() == TokenType.OPERATOR &&
          (currentToken.getValue().equals("!") || currentToken.getValue().equals("-"));
    }



    // IF, FOR, WHILE, PRINT - parsing functions

    /**
     * By the time we get here, we assume that we've already consumed the "for"
     */
    private ForStatement parseFor() throws ParseException {
        consumeTokenIfMatches(TokenType.DELIMITER, "(");

        Statement forInit = null;
        Expression forCondition = null;
        Statement forUpdate = null;

        // parse the init for
        if (!checkAndConsume(TokenType.DELIMITER, ";")) {
            if (isTypeToken(currentToken)) {
                forInit = new DeclarationStatement(parseVariableDeclaration());
            } else if (currentToken.getType() == TokenType.IDENTIFIER) {
                forInit = parseAssignmentStatement();
            } else {
                throw new ParseException("Invalid initialization in for loop");
            }
            consumeTokenIfMatches(TokenType.DELIMITER, ";");
        }

        // parse the condition for
        if (!checkAndConsume(TokenType.DELIMITER, ";")) {
            forCondition = parseExpression();
            consumeTokenIfMatches(TokenType.DELIMITER, ";");
        }

        // parse the update for
        if (!checkAndConsume(TokenType.DELIMITER, ")")) {
            if (currentToken.getType() == TokenType.IDENTIFIER) {
                forUpdate = parseAssignmentStatement();
            } else {
                throw new ParseException("Invalid update in for loop");
            }
            consumeTokenIfMatches(TokenType.DELIMITER, ")");
        }

        // consume the body of the for loop
        consumeTokenIfMatches(TokenType.DELIMITER, "{");
        CodeBlock body = parseCodeBlock();

        return new ForStatement(forInit, forCondition, forUpdate, body);
    }


    /**
     * By the time we get here, we assume that we've already consumed the "if"
     */
    private IfStatement parseIf() throws ParseException {
        // if condition
        consumeTokenIfMatches(TokenType.DELIMITER, "(");
        Expression condition = parseExpression();
        consumeTokenIfMatches(TokenType.DELIMITER, ")");

        // if body
        consumeTokenIfMatches(TokenType.DELIMITER, "{");
        CodeBlock thenBlock = parseCodeBlock();

        // else block
        CodeBlock elseBlock = null;
        if (checkAndConsume(TokenType.KEYWORD, "else")) {
            consumeTokenIfMatches(TokenType.DELIMITER, "{");
            elseBlock = parseCodeBlock();
        }

        return new IfStatement(condition, thenBlock, elseBlock);
    }

    /**
     * By the time we get here, we assume that we've already consumed the "while"
     */
    private WhileStatement parseWhile() throws ParseException {
        consumeTokenIfMatches(TokenType.DELIMITER, "(");
        Expression condition = parseExpression();
        consumeTokenIfMatches(TokenType.DELIMITER, ")");

        // handle the body of the while
        consumeTokenIfMatches(TokenType.DELIMITER, "{");
        CodeBlock body = parseCodeBlock();

        return new WhileStatement(condition, body);
    }

    /**
     * By the time we get here, we assume that we've already consumed the "print"
     */
    private Statement parsePrint() throws ParseException {
        consumeTokenIfMatches(TokenType.DELIMITER, "(");
        Expression expr = parseExpression();
        consumeTokenIfMatches(TokenType.DELIMITER, ")");
        consumeTokenIfMatches(TokenType.DELIMITER, ";");
        return new PrintStatement(expr);
    }


    // PARSE CODE BLOCKS

    /**
     * note that the CodeBlock handles the closing bracket of a code block here.
     */
    private CodeBlock parseCodeBlock() throws ParseException {
        List<Statement> parsedCodeBlock = parseStatementList();

        consumeTokenIfMatches(TokenType.DELIMITER, "}");
        return new CodeBlock(parsedCodeBlock);
    }

    // EXCEPTIONS & DEBUGGING
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Print debugging statements when debugMode
     * has been set to True. Set the flag to true to
     * debug print
     */
    private void debugPrint(String message) {
        boolean debugMode = false;

        if (debugMode) {
            System.out.println(message);
        }
    }

}