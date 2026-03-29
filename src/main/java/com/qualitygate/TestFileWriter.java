package com.qualitygate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFileWriter {

    public File saveTestFile(String repoPath, String className, String testCode) throws IOException {

        String cleanedCode = extractJavaCodeBlock(testCode);

        // FORCE correct test class name
        String forcedClassName = className + "Test";

        // Replace any public class declaration with correct name
        cleanedCode = cleanedCode.replaceAll(
                "public\\s+class\\s+\\w+",
                "public class " + forcedClassName
        );

        String testDirPath = repoPath + "/src/test/java/com/example/";
        File testDir = new File(testDirPath);
        if (!testDir.exists()) {
            testDir.mkdirs();
        }

        // Delete only this specific test file (not all tests!)
        File testFile = new File(testDirPath + forcedClassName + ".java");
        if (testFile.exists()) {
            testFile.delete();
        }

        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(cleanedCode);
        }

        System.out.println("Saved cleaned test file: " + testFile.getAbsolutePath());

        return testFile;
    }

    private String extractJavaCodeBlock(String text) {

        Pattern pattern = Pattern.compile("```java(.*?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // fallback: remove all backticks if no proper block
        return text.replace("```", "").trim();
    }

    private String extractPublicClassName(String code) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}