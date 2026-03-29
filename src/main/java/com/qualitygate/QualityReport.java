package com.qualitygate;

import java.util.*;


public class QualityReport {

    public Map<String, String> perFunctionStatus = new HashMap<>();
    public Set<String> failedMethods = new HashSet<>();
    public Set<String> hallucinatedMethods = new HashSet<>();

    // Test metrics
    public int totalTests;
    public int passed;
    public int failures;
    public int errors;
    public double passRate;
    public int staticHallucinations;
    public int hallucinations;
    public int missingSymbol;
    public int wrongSignature;
    public int typeMismatch;
    public int semanticMismatch;
    public int missingImport;
    public int logicalFailures;
    public double lineCoveragePercent;
    public int syntaxErrors;
    public boolean compilationFailed;

    // Mutation metrics
    public int totalMutants;
    public int killed;
    public int survived;
    public int noCoverage;
    public int executedMutants;
    public double overallKillRatio;
    public int timedOut;
    public double mutationScore;
    public double coveragePercent;

    public String finalDecision;
    public String decisionReason = "";
    public double qualityScore;

}