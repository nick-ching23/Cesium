package org.cesium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Scanner {
    private String sourceCode;
    private int currPosition;
    private int sourceCodeLength;
    private static final Set<String> KEYWORDS = new HashSet<>();
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
    public List<Token> scanSourceCode() {
        List<Token> tokens = new ArrayList<>(); // will hold list of tokens

        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);

            // skip over single line and multi-line comments
            if (isWhitespace(currentChar)) {
                currPosition++;
            } else if (currentChar == '/') {
                if (lookAhead(1) == '/') {
                    skipSingleLineComment();
                } else if (lookAhead(1) == '*') {
                    skipMultiLineComment();
                }
            }
            else if (isLetter(currentChar)) {
                tokens.add(scanIdentifierOrKeyword());
            }
            else if (isDigit(currentChar)) {
                tokens.add(scanNumericLiteral());
            }



        }




        return tokens;
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
        return "()[]{};,".indexOf(ch) != -1;
    }

    private boolean isOperator(char c) {
        return ("-+*=/<>|&!").indexOf(c) != -1;
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

    private Token scanNumericLiteral() {
        StringBuilder word = new StringBuilder();
        int state = 0; // State 0: starting state of FSM | State 1: identifying integers | State 2: floats

        while (currPosition < sourceCodeLength) {
            char currentChar = sourceCode.charAt(currPosition);

            // start state
            if (state == 0 && isDigit(currentChar)) {
                word.append(currentChar);
                currPosition++;
                state = 1;
            }
            // state 1: handling integers
            else if (state == 1 && isDigit(currentChar)) {
                word.append(currentChar);
                currPosition++;
            }
            // state 2: handling any numerical values with decimal points
            else if (state == 1 && currentChar == '.') {
                word.append(currentChar);
                currPosition++;
                state = 2;
            }
            // state 2: now a float
            else if (state == 2 && isDigit(currentChar)) {
                word.append(currentChar);
                currPosition++;
            } else {
                break;
            }
        }

        return new Token(TokenType.NUMERIC_LITERAL, word.toString());
    }

}
