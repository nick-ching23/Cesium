package org.cesium;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.io.File;

public class Main {

    /*
     * Main method will handle inputs from Cesium files with the suffix ".ces"
     * These files will be run through our scanner program and tokenized appropriately.
     * You can find these files in src/main/resources
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.cesium.Main <filename.ces>");
            System.exit(1);
        }

        String fileName = args[0];
        File file = new File(fileName);

        try {
            if (file.exists()) {

                // Read the contents of the file
                String code = new String(Files.readAllBytes(file.toPath()));
                Scanner scanner = new Scanner(code);
                List<Token> tokens = scanner.scanSourceCode();

                System.out.println(tokens);
                System.out.println("\n");

                Parser parser = new Parser(tokens);
                try {
                    ASTNode ast = parser.parse();

                    DisplayAST printer = new DisplayAST();
                    String astOutput = printer.toString(ast);
                    System.out.println(astOutput);

                } catch (Parser.ParseException e) {
                    System.err.println("Parse error: " + e.getMessage());
                }

            } else {
                System.err.println("File not found: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + fileName);
            e.printStackTrace();
        } catch (LexicalException e) {
            // Catch the lexical error and print the error message
            System.err.println("Lexical Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
