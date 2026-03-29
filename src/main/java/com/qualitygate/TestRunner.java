package com.qualitygate;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class TestRunner {

    public QualityReport runTests(String repoPath, int staticHallucinations, Set<String> calledMethods, Set<String> validMethods, String runName) {

        QualityReport report = new QualityReport();

        // Initialize all methods as NOT_TESTED
        for (String method : validMethods) {
            report.perFunctionStatus.put(method, "NOT_TESTED");
        }

        if (staticHallucinations == -1) {
            report.syntaxErrors++;
            report.compilationFailed = true;
            staticHallucinations = 0;
        }

        try {

            File jacoco = new File(repoPath + "/target/jacoco.exec");
            if (jacoco.exists()) {
                jacoco.delete();
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "cmd.exe", "/c", "mvn -Dtest=" + runName + "Test verify -Dmaven.test.failure.ignore=true"
            );

            builder.directory(new File(repoPath));
            builder.redirectErrorStream(true);

            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            int testsRun = 0;
            int failures = 0;
            int errors = 0;

            Pattern summaryPattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+)");

            Pattern failedTestPattern = Pattern.compile("com\\.example\\.(\\w+)Test\\.(\\w+)");

            String currentFailingMethod = null;

            while ((line = reader.readLine()) != null) {

                System.out.println(line);

                // Extract summary
                Matcher summaryMatcher = summaryPattern.matcher(line);
                if (summaryMatcher.find()) {
                    testsRun = Integer.parseInt(summaryMatcher.group(1));
                    failures = Integer.parseInt(summaryMatcher.group(2));
                    errors = Integer.parseInt(summaryMatcher.group(3));
                }

                // Extract failing test method
                Matcher failMatcher = failedTestPattern.matcher(line);
                if (failMatcher.find()) {

                    String testMethod = failMatcher.group(2);

                    if (testMethod.startsWith("test")) {

                        String sutMethod = testMethod.substring(4);

                        if (sutMethod.contains("_")) sutMethod = sutMethod.substring(0, sutMethod.indexOf("_"));

                        sutMethod = Character.toLowerCase(sutMethod.charAt(0)) + sutMethod.substring(1);

                        currentFailingMethod = sutMethod;
                        report.failedMethods.add(sutMethod);
                    }
                }

                // Detect exception mismatch (semantic hallucination)
                if (line.contains("Unexpected exception type thrown")
                        && currentFailingMethod != null) {

                    report.hallucinatedMethods.add(currentFailingMethod);
                }

                // Compiler hallucination detection
                if (line.contains("cannot find symbol"))
                    report.missingSymbol++;

                if (line.contains("does not exist"))
                    report.missingImport++;

                if (line.contains("cannot be applied to given types"))
                    report.wrongSignature++;

                if (line.contains("incompatible types"))
                    report.typeMismatch++;
                
                if (line.contains("Unexpected exception type thrown") && currentFailingMethod != null) {
                    report.semanticMismatch++;
                    report.hallucinatedMethods.add(currentFailingMethod);
                }

            }

            process.waitFor();

            parseJaCoCoCoverage(repoPath, report);

            // Compute totals
            report.totalTests = testsRun;
            report.failures = failures;
            report.errors = errors;
            report.passed = testsRun - failures - errors;
            report.passRate =
                    testsRun > 0 ? (report.passed * 100.0) / testsRun : 0.0;

            report.hallucinations =
                    report.missingSymbol
                            + report.missingImport
                            + report.wrongSignature
                            + report.typeMismatch
                            + report.syntaxErrors
                            + report.semanticMismatch
                            + staticHallucinations;

            // -----------------------------
            // Per-Function Status Assignment
            // -----------------------------
            for (String method : validMethods) {

                if (!calledMethods.contains(method)) {
                    report.perFunctionStatus.put(method, "NOT_TESTED");
                    continue;
                }

                if (report.hallucinatedMethods.contains(method)) {
                    report.perFunctionStatus.put(method, "HALLUCINATION");
                    continue;
                }

                if (report.failedMethods.contains(method)) {
                    report.perFunctionStatus.put(method, "FAIL");
                    continue;
                }

                report.perFunctionStatus.put(method, "PASS");
            }

            // Mutation analysis only if clean
            if (!report.compilationFailed
                    && report.totalTests > 0
                    && report.failures == 0
                    && report.errors == 0
                    && report.hallucinations == 0) {

                runMutationAnalysis(repoPath, report);
            }

            computeFinalDecision(report);

            exportJsonReport(repoPath, report, runName);
            exportHtmlReport(repoPath, report, runName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return report;
    }

    // =============================================
    // RUN PIT
    // =============================================
    private void runMutationAnalysis(String repoPath, QualityReport report) {

        try {
            System.out.println("\n==== RUNNING MUTATION ANALYSIS ====");

            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    "mvn pitest:mutationCoverage"
            );

            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains("COMPILATION ERROR")) {
                    report.compilationFailed = true;
                }
                if (line.contains("')' expected") ||
                    line.contains("',' expected") ||
                    line.contains("Parse error")) {

                    report.syntaxErrors++;
                    report.compilationFailed = true;
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Maven exited with code: " + exitCode);

            parseMutationReport(repoPath, report);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =============================================
    // PARSE mutations.xml
    // =============================================
    private void parseMutationReport(String repoPath, QualityReport report) {

        try {
            File pitRoot = new File(repoPath + "/target/pit-reports");

            if (!pitRoot.exists() || !pitRoot.isDirectory()) {
                System.out.println("No PIT report folder found.");
                return;
            }

            File xmlFile = new File(pitRoot, "mutations.xml");

            if (!xmlFile.exists()) {
                System.out.println("mutations.xml not found.");
                return;
            }

            int killed = 0;
            int survived = 0;
            int noCoverage = 0;
            int timedOut = 0;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            NodeList mutations = doc.getElementsByTagName("mutation");

            for (int i = 0; i < mutations.getLength(); i++) {
                Element m = (Element) mutations.item(i);
                String status = m.getAttribute("status");

                switch (status) {
                    case "KILLED" -> killed++;
                    case "SURVIVED" -> survived++;
                    case "NO_COVERAGE" -> noCoverage++;
                    case "TIMED_OUT" -> timedOut++;
                }
            }

            int executed = killed + survived;
            int total = killed + survived + noCoverage + timedOut;

            double mutationStrength = executed == 0 ? 0 :
                    (killed * 100.0) / executed;

            double overallKillRatio = total == 0 ? 0 :
                    (killed * 100.0) / total;

            report.totalMutants = total;
            report.executedMutants = executed;
            report.killed = killed;
            report.survived = survived;
            report.noCoverage = noCoverage;
            report.timedOut = timedOut;
            report.mutationScore = mutationStrength;
            report.overallKillRatio = overallKillRatio;

            System.out.println("\n==== MUTATION SUMMARY ====");
            System.out.println("Total Mutants: " + total);
            System.out.println("Executed Mutants: " + executed);
            System.out.println("Killed: " + killed);
            System.out.println("Survived: " + survived);
            System.out.println("No Coverage: " + noCoverage);
            System.out.println("Timed Out: " + timedOut);
            System.out.printf("Mutation Score: %.2f%%\n", mutationStrength);
            System.out.println("Overall Kill Ratio: " + overallKillRatio);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseJaCoCoCoverage(String repoPath, QualityReport report) {

        try {
            File jacocoXml = new File(repoPath + "/target/site/jacoco/jacoco.xml");

            if (!jacocoXml.exists()) {
                System.out.println("JaCoCo XML not found.");
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            factory.setNamespaceAware(false);
            factory.setValidating(false);

            DocumentBuilder builder = factory.newDocumentBuilder();

            // stops report.dtd loading
            builder.setEntityResolver((publicId, systemId) ->
                    new org.xml.sax.InputSource(new java.io.StringReader("")));

            Document doc = builder.parse(jacocoXml);
            doc.getDocumentElement().normalize();

            NodeList counters = doc.getElementsByTagName("counter");

            for (int i = 0; i < counters.getLength(); i++) {

                Element counter = (Element) counters.item(i);

                if ("LINE".equals(counter.getAttribute("type"))) {

                    int missed = Integer.parseInt(
                            counter.getAttribute("missed"));
                    int covered = Integer.parseInt(
                            counter.getAttribute("covered"));

                    int total = missed + covered;

                    double percent = total == 0 ? 0 :
                            (covered * 100.0) / total;

                    report.lineCoveragePercent = percent;
                    report.coveragePercent = percent;

                    System.out.printf(
                            "Line Coverage: %.2f%%\n",
                            percent
                    );
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // =============================================
    // FINAL DECISION
    // =============================================
    private void computeFinalDecision(QualityReport report) {

        // Hard gate conditions
        if (report.compilationFailed) {
            report.finalDecision = "FAIL";
            report.qualityScore = 0;
            report.decisionReason = "Compilation failed";
            return;
        }

        if (report.failures > 0) {
            report.finalDecision = "FAIL";
            report.qualityScore = 0;
            report.decisionReason = "Test failures detected: " + report.failures;
            return;
        }

        if (report.errors > 0) {
            report.finalDecision = "FAIL";
            report.qualityScore = 0;
            report.decisionReason = "Test errors detected: " + report.errors;
            return;
        }

        if (report.hallucinations > 0) {
            report.finalDecision = "FAIL";
            report.qualityScore = 0;
            report.decisionReason = "Hallucinations detected: " + report.hallucinations;
            return;
        }

        double coverage = report.lineCoveragePercent;
        double mutation = report.mutationScore;
        double passRate = report.passRate;
        double hallucinationPenalty =
                report.hallucinations == 0 ? 100 : 0;

        double score =
                (0.30 * coverage) +
                (0.40 * mutation) +
                (0.20 * passRate) +
                (0.10 * hallucinationPenalty);

        report.qualityScore = score;

        if (score >= 75) {
            report.finalDecision = "PASS";
            report.decisionReason = "Quality score above threshold";
        } else {
            report.finalDecision = "FAIL";
            report.decisionReason = "Quality score below threshold";
        }
    }

    // =============================================
    // EXPORT JSON
    // =============================================
    private void exportJsonReport(String repoPath, QualityReport report, String runName) {

        try {
            File output = new File(repoPath + "/quality-report-" + runName + ".json");
            PrintWriter writer = new PrintWriter(output);

            writer.println("{");
            writer.println("  \"finalDecision\": \"" + report.finalDecision + "\",");
            writer.println("  \"testSummary\": {");
            writer.println("    \"total\": " + report.totalTests + ",");
            writer.println("    \"passed\": " + report.passed + ",");
            writer.println("    \"failures\": " + report.failures + ",");
            writer.println("    \"errors\": " + report.errors + ",");
            writer.println("    \"passRate\": " + report.passRate);
            writer.println("  },");
            writer.println("  \"mutationSummary\": {");
            writer.println("    \"totalMutants\": " + report.totalMutants + ",");
            writer.println("    \"killed\": " + report.killed + ",");
            writer.println("    \"survived\": " + report.survived + ",");
            writer.println("    \"noCoverage\": " + report.noCoverage + ",");
            writer.println("    \"timedOut\": " + report.timedOut + ",");
            writer.println("    \"mutationScore\": " + report.mutationScore + ",");
            writer.println("    \"coveragePercent\": " + report.coveragePercent);
            writer.println("  },");
            writer.println("  \"hallucinationSummary\": {");
            writer.println("    \"missingSymbol\": " + report.missingSymbol + ",");
            writer.println("    \"missingImport\": " + report.missingImport + ",");
            writer.println("    \"wrongSignature\": " + report.wrongSignature + ",");
            writer.println("    \"typeMismatch\": " + report.typeMismatch + ",");
            writer.println("    \"syntaxErrors\": " + report.syntaxErrors + ",");
            writer.println("    \"semanticMismatch\": " + report.semanticMismatch + ",");
            writer.println("    \"total\": " + report.hallucinations);
            writer.println("  },");
            writer.println("  \"logicalFailures\": " + report.logicalFailures + ",");
            writer.println("  \"coverageSummary\": {");
            writer.println("    \"lineCoveragePercent\": " + report.lineCoveragePercent);
            writer.println("  }");
            writer.println("  ,\"perFunctionStatus\": {");
            int count = 0;
            int size = report.perFunctionStatus.size();

            for (String key : report.perFunctionStatus.keySet()) {
                String value = report.perFunctionStatus.get(key);

                writer.print("    \"" + key + "\": \"" + value + "\"");

                if (++count < size) writer.println(",");
                else writer.println();
            }
            writer.println("  }");
            writer.println("}");

            writer.close();

            System.out.println("\nJSON report generated at:");
            System.out.println(output.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =============================================
    // EXPORT HTML
    // =============================================
    private void exportHtmlReport(String repoPath, QualityReport report, String runName) {
        try {

            File output = new File(repoPath +
                    "/quality-report-" + runName + ".html");

            PrintWriter writer = new PrintWriter(output);

            writer.println("""
            <html>
            <head>
                <title>Quality Gate Report</title>
                <style>
                    body { font-family: Arial; margin: 40px; }
                    h1 { color: #2c3e50; }
                    .pass { color: green; font-weight: bold; }
                    .fail { color: red; font-weight: bold; }
                    .section { margin-top: 30px; }
                    table {
                        border-collapse: collapse;
                        width: 60%;
                    }
                    th, td {
                        border: 1px solid #ddd;
                        padding: 8px;
                    }
                    th {
                        background-color: #f2f2f2;
                    }
                </style>
            </head>
            <body>
            """);

            writer.println("<h1>Quality Gate Report - " + runName + "</h1>");

            // =============================
            // TEST SUMMARY
            // =============================
            writer.println("<div class='section'><h2>Test Summary</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Total</th><th>Passed</th><th>Failures</th><th>Errors</th><th>Pass Rate</th></tr>");
            writer.println("<tr>");
            writer.println("<td>" + report.totalTests + "</td>");
            writer.println("<td class='pass'>" + report.passed + "</td>");
            writer.println("<td class='fail'>" + report.failures + "</td>");
            writer.println("<td>" + report.errors + "</td>");
            writer.println("<td>" + String.format("%.2f", report.passRate) + "%</td>");
            writer.println("</tr>");
            writer.println("</table></div>");

            // =============================
            // COVERAGE
            // =============================

            writer.println("<div class='section'><h2>Coverage</h2>");
            writer.println("<p>Line Coverage: <b>"
                    + String.format("%.2f", report.lineCoveragePercent)
                    + "%</b></p></div>");

            // =============================
            // MUTATION
            // =============================

            writer.println("<div class='section'><h2>Mutation Summary</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Total</th><th>Executed</th><th>Killed</th><th>Survived</th><th>No Coverage</th><th>Strength</th><th>Overall Kill %</th></tr>");
            writer.println("<tr>");
            writer.println("<td>" + report.totalMutants + "</td>");
            writer.println("<td>" + report.executedMutants + "</td>");
            writer.println("<td class='pass' style='color:green'>" + report.killed + "</td>");
            writer.println("<td class='fail' style='color:red'>" + report.survived + "</td>");
            writer.println("<td>" + report.noCoverage + "</td>");
            writer.println("<td>" + String.format("%.2f", report.mutationScore) + "%</td>");
            writer.println("<td>" + String.format("%.2f", report.overallKillRatio) + "%</td>");
            writer.println("</tr>");
            writer.println("</table></div>");

            // =============================
            // HALLUCINATION
            // =============================

            writer.println("<div class='section'><h2>Hallucination Summary</h2>");
            writer.println("<ul>");
            writer.println("<li>Missing Symbol: " + report.missingSymbol + "</li>");
            writer.println("<li>Missing Import: " + report.missingImport + "</li>");
            writer.println("<li>Wrong Signature: " + report.wrongSignature + "</li>");
            writer.println("<li>Type Mismatch: " + report.typeMismatch + "</li>");
            writer.println("<li>Semantic Mismatch: " + report.semanticMismatch + "</li>");
            writer.println("<li>Total: <b>" + report.hallucinations + "</b></li>");
            writer.println("</ul></div>");

            // =============================
            // PER FUNCTION STATUS
            // =============================

            writer.println("<div class='section'><h2>Per-Function Status</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Function</th><th>Status</th></tr>");

            for (Map.Entry<String, String> entry :
                    report.perFunctionStatus.entrySet()) {

                String statusClass =
                        entry.getValue().equals("PASS") ? "pass" : "fail";

                writer.println("<tr>");
                writer.println("<td>" + entry.getKey() + "</td>");
                writer.println("<td class='" + statusClass + "'>"
                        + entry.getValue() + "</td>");
                writer.println("</tr>");
            }

            writer.println("</table></div>");

            // =============================
            // FINAL DECISION
            // =============================
            writer.println("<div class='section'>");
            writer.println("<h2>Final Quality Gate</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Decision</th><th>Quality Score</th><th>Reason</th></tr>");
            String color = report.finalDecision.equals("PASS") ? "green" : "red";
            writer.println("<tr>");
            writer.println("<td style='color:" + color + "; font-weight:bold;'>"+ report.finalDecision + "</td>");
            writer.println("<td>" + String.format("%.2f", report.qualityScore) + " / 100</td>");
            writer.println("<td>" + report.decisionReason + "</td>");
            writer.println("</tr>");
            writer.println("</table>");
            writer.println("</div>");

            writer.println("</body></html>");

            writer.close();

            System.out.println("HTML report generated at:");
            System.out.println(output.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}