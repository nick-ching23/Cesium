package org.cesium;

import java.util.ArrayList;
import java.util.List;

public class Scanner {
    private String sourceCode;
    private int currPosition;
    private int sourceCodeLength;

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

            if (isWhitespace(currentChar)) {
                currPosition++;
            } else if (currentChar == '/') {
                if (lookAhead(1) == '/') {
                    skipSingleLineComment();
                } else if (lookAhead(1) == '*') {
                    skipMultiLineComment();
                }
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

}
