package org.cesium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Scanner {
    private String sourceCode;
    private int currPosition;
    private int sourceCodeLength;
    private int line = 1;
    private static final Set<String> KEYWORDS = new HashSet<>();

    // defines keywords for this language
    static {
        KEYWORDS.add("Stream");
        KEYWORDS.add("Reactive");
        KEYWORDS.add("if");
        KEYWORDS.add("else");
        KEYWORDS.add("for");
        KEYWORDS.add("return");
        KEYWORDS.add("print");
        KEYWORDS.add("reactive");
    }

    // given source code, we identify position. This allows iterating through source code
    // to identify tokens
    public Scanner(String sourceCode) {
        this.sourceCode = sourceCode;
        this.sourceCodeLength = sourceCode.length();
        this.currPosition = 0;
    }

    /*
     * The scanner method will take source code written in Cesium, and identify
     * tokenize the source code. Each token will represent specific parts of the
     * lexical grammar of our programming language defined in the README
     */
    public List<Token> scanSourceCode() throws LexicalException {
        List<Token> tokens = new ArrayList<>(); // will hold list of tokens

        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);

            // skip over single line and multi-line comments
            if (isWhitespace(currentChar)) {
                skipWhitespace();
            } else if (currentChar == '/') {
                if (lookAhead(1) == '/') {
                    skipSingleLineComment();
                } else if (lookAhead(1) == '*') {
                    skipMultiLineComment();
                }
            } else if (isLetter(currentChar)) {
                tokens.add(scanIdentifierOrKeyword());
            } else if (isDigit(currentChar)) {
                tokens.add(scanNumericLiteral());
            } else if (currentChar == '"') {
                tokens.add(scanStringLiteral());
            } else if (isOperator(currentChar)) {
                tokens.add(scanOperator());
            } else if (isDelimiter(currentChar)) {

                if (!isInvalidTokenAfterDelimiter()) {
                    tokens.add(new Token(TokenType.DELIMITER, Character.toString(currentChar)));
                    currPosition++;
                }
                else {
                    throw new LexicalException("Invalid numeric literal ending with a dot at line " + line);
                }
            } else {
                // Lexical Error
                throw new LexicalException("Unrecognized token '" + currentChar + "' at line " + line);
            }

        }


        return tokens;
    }

    // allows us to keep track of what line we are on for potential error handling
    private void skipWhitespace() {
        while (currPosition < sourceCodeLength && isWhitespace(sourceCode.charAt(currPosition))) {
            if (sourceCode.charAt(currPosition) == '\n') {
                line++;
            }
            currPosition++;
        }
    }

    // Helper Methods for identifying char type
    private boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    private boolean isDigit(char c) {
        return Character.isDigit(c);
    }

    private boolean isWhitespace(char c) {
        return c == '\n' || c == '\t' || c == '\r' || c == ' ';
    }

    private boolean isDelimiter(char ch) {
        return "()[]{};,.".indexOf(ch) != -1;
    }

    private boolean isOperator(char c) {
        return ("-+*=/<>|&!").indexOf(c) != -1;
    }

    // Helper method to check for multi-character operators
    private boolean isMultipartOperator(char charOne, char charTwo) {
        return (charOne == '=' && charTwo == '=') || (charOne == '!' && charTwo == '=')
          || (charOne == '<' && charTwo == '=') || (charOne == '>' && charTwo == '=')
          || (charOne == '&' && charTwo == '&') || (charOne == '|' && charTwo == '|');
    }

    private boolean isKeyword(String word) {
        return KEYWORDS.contains(word);  // This ensures exact matches only
    }

    // an implementation of lookahead that aids in determining multi character
    // tokens (like '//' vs '/')
    private char lookAhead(int offset) {
        if (currPosition + offset < sourceCodeLength) {
            return sourceCode.charAt(currPosition + offset);
        }
        return '\0';
    }

    /*
     * we will not tokenize single line comments. skip ahead until comment complete.
     * assumes source code file will be read in line by line.
     */
    private void skipSingleLineComment() {
        currPosition += 2; // Skip "//"
        while (currPosition < sourceCodeLength && sourceCode.charAt(currPosition) != '\n') {
            currPosition++;
        }
    }

    /*
     * we will not tokenize multi line comments. skip ahead until comment complete.
     * assumes source code file will be read in line by line.
     */
    private void skipMultiLineComment() {
        currPosition += 2; // Skip "/*"
        while (currPosition < sourceCodeLength) {

            // check if the string starting at the curr position starts with */
            if (sourceCode.startsWith("*/", currPosition)) {
                currPosition += 2;
                break;
            }
            currPosition++;
        }
    }

    /*
     * Tokenizes any identifiers or keywords
     */
    private Token scanIdentifierOrKeyword() {
        StringBuilder word = new StringBuilder();
        int state = 0; // State 0: starting state of FSM | State 1: identifying keyword/identifier

        // iterate to find the possible token.
        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);

            if (state == 0 && isLetter(currentChar)) {
                word.append(currentChar);
                currPosition++;
                state = 1;
            } else if (state == 1 && (isLetter(currentChar) || isDigit(currentChar))) {
                word.append(currentChar);
                currPosition++;
            } else {
                break;
            }
        }

        // stores the identifier or keyword as a token.
        String token = word.toString();
        if (isKeyword(token)) {
            return new Token(TokenType.KEYWORD, token);
        } else if (token.equals("true") || token.equals("false")) {
            return new Token(TokenType.BOOLEAN_LITERAL, token);
        } else {
            return new Token(TokenType.IDENTIFIER, token);
        }
    }

    /*
     * Tokenizes any numeric literals
     */
    private Token scanNumericLiteral() throws LexicalException {
        StringBuilder word = new StringBuilder();
        boolean hasDot = false;

        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);
            if (isDigit(currentChar)) {
                word.append(currentChar);
                currPosition++;
            }
            else if (currentChar == '.' && !hasDot) {
                hasDot = true;
                word.append(currentChar);
                currPosition++;
            }
            else if (currentChar == '.') {
                throw new LexicalException("Invalid numeric literal with multiple dots at line " + line);
            } else {
                break;
            }
        }

        // Check if the numeric literal ends with a dot (e.g., "1.")
        if (hasDot && word.charAt(word.length() - 1) == '.') {
            throw new LexicalException("Invalid numeric literal ending with a dot at line " + line);
        }

        return new Token(TokenType.NUMERIC_LITERAL, word.toString());
    }

    /*
     * Tokenizes any string literals
     */
    private Token scanStringLiteral() throws LexicalException{
        StringBuilder word = new StringBuilder();
        currPosition++; // Skip opening quotation mark

        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);

            if (currentChar == '"') {
                currPosition++; // skip closing quotation mark
                return new Token(TokenType.STRING_LITERAL, word.toString());
            } else {
                word.append(currentChar);
                currPosition++;
            }
        }

        // if there is no closing "" mark. then the rest of the source code will be interpreted
        // as a single token. this is obviously unknown and a grammar error.
        throw new LexicalException("Unterminated string literal at line " + line);
    }

    /*
     * Tokenizes any operators
     */
    private Token scanOperator() {
        StringBuilder word = new StringBuilder();
        word.append(sourceCode.charAt(currPosition));
        currPosition++;

        // check if the code is multiline
        if (currPosition < sourceCodeLength) {
            char nextChar = sourceCode.charAt(currPosition);
            if (isMultipartOperator(word.charAt(0), nextChar)) {
                word.append(nextChar);
                currPosition++;
            }
        }
        return new Token(TokenType.OPERATOR, word.toString());
    }

    // Checks we don't have a possible error like ".1"
    private boolean isInvalidTokenAfterDelimiter() {
        if (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);
            if (currentChar == '.' && isDigit(lookAhead(1))) {
                return true;
            }
        }
        return false;
    }
}
