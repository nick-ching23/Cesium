package org.cesium;

import java.util.List;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {

        Scanner scanner = new Scanner("for true  411   return");
        List<Token> results = scanner.scanSourceCode();

        System.out.println(results);

    }
}