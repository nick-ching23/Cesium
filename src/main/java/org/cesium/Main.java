package org.cesium;

import java.io.IOException;
import java.nio.file.Files;
import java.io.File;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.cesium.Main <filename.ces>");
            System.exit(1);
        }

        String fileName = args[0];
        File file = new File(fileName);

        try {
            if (file.exists()) {
                String code = new String(Files.readAllBytes(file.toPath()));

                // Lexical Analysis
                Scanner scanner = new Scanner(code);
                List<Token> tokens = scanner.scanSourceCode();
                System.out.println(tokens);

                // Parsing
                Parser parser = new Parser(tokens);
                ASTNode.CesiumProgram ast = parser.parse();
                DisplayAST printer = new DisplayAST();
                System.out.println(printer.toString(ast));

                // Code Generation
                CodeGenerator cg = new CodeGenerator();
                cg.generateCode(ast, "MyCesiumProgram");
                System.out.println("Compilation successful. Generated MyCesiumProgram.class");

            } else {
                System.err.println("File not found: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + fileName);
            e.printStackTrace();
        } catch (LexicalException e) {
            System.err.println("Lexical Error: " + e.getMessage());
            System.exit(1);
        } catch (Parser.ParseException e) {
            System.err.println("Parse error: " + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.err.println("Code generation error: " + e.getMessage());
            System.exit(1);
        }
    }
}