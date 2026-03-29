package com.qualitygate;

import java.util.*;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;

public class Main {

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("Usage: java -jar tool.jar <repo-path>");
            return;
        }

        String repoPath = args[0];

        String runId = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        RepoIntakeService intakeService = new RepoIntakeService();
        List<TestTarget> targets = intakeService.scanRepository(repoPath);

        System.out.println("==== Detected Test Targets ====");

        // Group targets by class
        Map<String, List<TestTarget>> classMap = new HashMap<>();

        for (TestTarget target : targets) {
            classMap
                .computeIfAbsent(target.getClassName(), k -> new ArrayList<>())
                .add(target);
        }

        LLMService llm = new LLMService();
        TestFileWriter writer = new TestFileWriter();
        TestRunner runner = new TestRunner();

        // Process each class independently
        for (String className : classMap.keySet()) {

            System.out.println("\n==== Processing Class: " + className + " ====");

            List<TestTarget> classMethods = classMap.get(className);

            // Build method list block for prompt
            StringBuilder methodsBlock = new StringBuilder();

            for (TestTarget t : classMethods) {
                methodsBlock.append("- ")
                        .append(t.getMethodSignature())
                        .append("\n");
            }

            String prompt = """
                Generate a JUnit 5 test class.

                STRICT RULES:
                - Class under test: %s
                - Package: com.example

                You MUST follow these exact constructor and method signatures:

                Constructors:
                %s

                Public Methods:
                %s

                CRITICAL:
                - Use ONLY the listed constructors.
                - Use ONLY the listed methods.
                - Respect return types exactly.
                - If method returns void, do NOT use it inside assertEquals.
                - For void methods, verify state using getters.
                - Do NOT invent constructors.
                - Do NOT invent setters.
                - Do NOT create new APIs.
                - Do NOT use reflection.
                - Do NOT modify object structure.
                - Do not use @Override

                Return only valid Java code.
                No explanations.
                No markdown.
                No backticks.

                Provide full test class code.
                """.formatted(
                        className,
                        intakeService.getConstructorSignatures(className, repoPath),
                        methodsBlock.toString()
                );

            try {

                // 1️⃣ Generate test
                String response =
                        llm.generateTestsWithTrace(prompt, className, repoPath, runId);

                writer.saveTestFile(repoPath, className, response);

                File testFile = new File(
                        repoPath + "/src/test/java/com/example/"
                                + className + "Test.java"
                );

                // 2️⃣ Static validation
                StaticSymbolValidator validator = new StaticSymbolValidator();

                Set<String> calledMethods =
                        validator.extractCalledMethods(testFile);

                int staticHallucinations =
                        validator.validate(testFile, classMethods);

                if (staticHallucinations == -1) {
                    System.out.println("Static Syntax Error Detected");
                    staticHallucinations = 0;
                } else {
                    System.out.println("Static Hallucinations Detected: "
                            + staticHallucinations);
                }

                // 3️⃣ Collect valid methods for this class
                Set<String> validMethods = new HashSet<>();

                for (TestTarget t : classMethods) {
                    validMethods.add(t.getMethodName());
                }

                // 4️⃣ Run quality gate for this class
                runner.runTests(
                        repoPath,
                        staticHallucinations,
                        calledMethods,
                        validMethods,
                        className   // JSON file name identifier
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("\n==== Quality Gate Execution Complete ====");
    }
}