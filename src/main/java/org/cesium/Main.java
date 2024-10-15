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

                for (Token token : tokens) {
                    System.out.println(token);
                }
            } else {
                System.err.println("File not found: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + fileName);
            e.printStackTrace();
        }
    }
}
