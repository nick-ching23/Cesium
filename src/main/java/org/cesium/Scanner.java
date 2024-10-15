package org.cesium;

public class Scanner {
    private String input;
    private int pos;
    private int length;






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
}
