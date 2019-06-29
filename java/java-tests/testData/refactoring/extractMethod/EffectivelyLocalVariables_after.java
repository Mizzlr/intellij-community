import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EffectivelyLocalVariables {
    void test() {
        Scanner inputStreamScanner = null;
        String theFirstLineFromDestinationFile;
        String originContent = "aaa";
        String fileName = "bbb";

        ddd(originContent, fileName);
    }

    private void ddd(String originContent, String fileName) {
        Scanner inputStreamScanner;
        String theFirstLineFromDestinationFile;
        newMethod(originContent, fileName);
    }

    private void newMethod(String originContent, String fileName) {
        Scanner inputStreamScanner;
        String theFirstLineFromDestinationFile;
        try {
            inputStreamScanner =
                    new Scanner(new File(fileName));
            theFirstLineFromDestinationFile = inputStreamScanner.nextLine();
            assertEquals(theFirstLineFromDestinationFile, originContent);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    void dup() {
        Scanner inputStreamScanner = null;
        String theFirstLineFromDestinationFile;
        String originContent = "";
        String fileName = "";

        newMethod(originContent, fileName);

    }
}