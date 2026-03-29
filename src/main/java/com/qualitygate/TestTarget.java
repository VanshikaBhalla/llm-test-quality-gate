package com.qualitygate;

public class TestTarget {

    private String className;
    private String methodName;
    private String methodSignature;
    private String filePath;

    public TestTarget(String className, String methodName,
                      String methodSignature, String filePath) {
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
        this.filePath = filePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return "Class: " + className +
               "\n  Method: " + methodSignature +
               "\n  File: " + filePath + "\n";
    }
}