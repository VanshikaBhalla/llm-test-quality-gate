package com.qualitygate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StaticSymbolValidator {

    public Set<String> extractCalledMethods(File testFile) {

        Set<String> calledMethods = new HashSet<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(testFile);

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {

                String methodName = call.getNameAsString();

                if (methodName.startsWith("assert")) continue;

                calledMethods.add(methodName);
            }

        } catch (Exception e) {
            System.out.println("Static parse failed: " + e.getMessage());
        }

        return calledMethods;
    }

    public int validate(File testFile, List<TestTarget> validTargets) {

        int hallucinationCount = 0;

        try {
            CompilationUnit cu = StaticJavaParser.parse(testFile);

            // Collect valid method names
            Set<String> validMethods = new HashSet<>();
            for (TestTarget t : validTargets) {
                validMethods.add(t.getMethodName());
            }

            // Extract method calls
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {

                String methodName = call.getNameAsString();

                // Ignore JUnit assertions
                if (methodName.startsWith("assert")) continue;

                // If method not in valid public methods → hallucination
                if (!validMethods.contains(methodName)) {
                    hallucinationCount++;
                }
            }

        } catch (Exception e) {

            // 🔥 THIS IS CRITICAL
            // If parsing fails → syntax hallucination
            System.out.println("Static symbol validation failed: " + e.getMessage());
            return -1;  // special value meaning syntax error
        }
        return hallucinationCount;
    }
}