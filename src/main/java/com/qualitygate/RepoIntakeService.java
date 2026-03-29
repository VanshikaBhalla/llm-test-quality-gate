package com.qualitygate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Paths;

public class RepoIntakeService {

    public List<TestTarget> scanRepository(String repoPath) {
        List<TestTarget> targets = new ArrayList<>();
        File root = new File(repoPath);
        scanFolder(root, targets);
        return targets;
    }

    public String getSourceCode(Path filePath) throws IOException {
       return Files.readString(filePath);
    }

    private void scanFolder(File folder, List<TestTarget> targets) {

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {

            if (file.isDirectory()) {
                scanFolder(file, targets);
            }

            else if (file.getName().endsWith(".java")
                    && !file.getPath().contains("test")
                    && !file.getPath().contains("target")) {

                parseJavaFile(file, targets);
            }
        }
    }

    private void parseJavaFile(File file, List<TestTarget> targets) {

        try {
            CompilationUnit cu = StaticJavaParser.parse(file);

            cu.findAll(ClassOrInterfaceDeclaration.class)
              .forEach(clazz -> {

                  for (MethodDeclaration method : clazz.getMethods()) {

                      if (method.isPublic()
                        && !method.isAbstract()
                        && !method.isStatic()) {

                          TestTarget target = new TestTarget(
                                  clazz.getNameAsString(),
                                  method.getNameAsString(),
                                  method.getDeclarationAsString(false, false, false),
                                  file.getAbsolutePath()
                          );

                          targets.add(target);
                      }
                  }
              });

        } catch (IOException e) {
            System.out.println("Error reading file: " + file.getName());
        }
    }

    public String getConstructorSignatures(String className, String repoPath) {
        try {
            String sourcePath = repoPath + "/src/main/java/com/example/" + className + ".java";
            File file = new File(sourcePath);

            if (!file.exists()) {
                return "No constructors found.";
            }

            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));

            com.github.javaparser.ast.CompilationUnit cu =
                    com.github.javaparser.StaticJavaParser.parse(content);

            StringBuilder constructors = new StringBuilder();

            cu.findAll(com.github.javaparser.ast.body.ConstructorDeclaration.class)
                    .forEach(c -> constructors.append("- public ")
                            .append(c.getNameAsString())
                            .append("(")
                            .append(
                                    c.getParameters().stream()
                                            .map(p -> p.getType() + " " + p.getName())
                                            .reduce((a, b) -> a + ", " + b)
                                            .orElse("")
                            )
                            .append(")\n")
                    );

            if (constructors.length() == 0) {
                constructors.append("- public ").append(className).append("()");
            }

            return constructors.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Unable to extract constructors.";
        }
    }
}