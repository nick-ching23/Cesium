package org.cesium;

import java.util.List;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {

        String inputProgram = "print \"Hello, World!\" + return || true <= 4.0";

        Scanner scanner = new Scanner(inputProgram);
        List<Token> results = scanner.scanSourceCode();

        System.out.println(results);

    }
}