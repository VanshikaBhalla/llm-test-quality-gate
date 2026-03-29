# LLM Test Quality Gate Tool

A Java-based tool to evaluate the quality of LLM-generated unit tests using multiple software testing metrics.

---

## 🚀 Features

* ✅ Test execution using Maven
* ✅ Static hallucination detection (compile-time issues)
* ✅ Runtime hallucination detection (semantic mismatches)
* ✅ JaCoCo line coverage analysis
* ✅ PIT mutation testing (only when tests are clean)
* ✅ Per-function status evaluation:

  * PASS
  * FAIL
  * HALLUCINATION
  * NOT_TESTED
* ✅ Final Quality Gate decision with reasoning
* ✅ HTML and JSON report generation

---

## 📂 Project Structure

```
llm-test-quality-gate/
│
├── src/main/java/com/qualitygate/
│   ├── Main.java
│   ├── TestRunner.java
│   ├── QualityReport.java
│   ├── LLMService.java
│   ├── RepoIntakeService.java
│   ├── StaticSymbolValidator.java
│   ├── TestFileWriter.java
│   └── TestTarget.java
│
├── sample-project/
├── sample-project2/
│
├── pom.xml
├── .gitignore
└── README.md
```

---

## ⚙️ Requirements

* Java 17+
* Maven
* Git

---

## 🛠️ Setup Instructions

### 1. Clone the Repository

```
git clone https://github.com/<your-username>/llm-test-quality-gate.git
cd llm-test-quality-gate
```

---

### 2. Add Sample Projects

Place your test subject projects inside the root directory:

```
sample-project/
sample-project2/
```

These should be **valid Maven projects** with:

```
src/main/java/
src/test/java/
pom.xml
```

---

### 3. Build the Tool

```
mvn clean install
```

---

## ▶️ How to Run

Run the tool using:

```
cd ../sample-project
mvn exec:java -Dexec.mainClass="com.qualitygate.Main"
```

---

## 📊 What the Tool Does

For each target class:

1. Runs unit tests using Maven
2. Parses test results (pass/fail/errors)
3. Detects hallucinations:

   * Missing symbols
   * Wrong signatures
   * Type mismatches
   * Semantic mismatches (exception differences)
4. Computes coverage using JaCoCo
5. Runs mutation testing (only if all tests pass and no hallucinations)
6. Assigns per-function status
7. Computes final Quality Gate decision

---

## 📈 Output

The tool generates:

### HTML Report

```
quality-report-<Class>.html
```

### JSON Report

```
quality-report-<Class>.json
```

---

## 🧪 Sample Projects

### sample-project

* Simple example (e.g., Calculator)
* Used for validating correctness of tool

### sample-project2

* More complex example (e.g., BankAccount)
* Demonstrates:

  * Runtime hallucinations
  * Exception mismatches
  * Partial test failures

---

## 🚦 Quality Gate Logic

### Hard Fail Conditions

The project is marked **FAIL immediately if:**

* Compilation fails
* Test failures > 0
* Test errors > 0
* Hallucinations detected

---

### Quality Score Formula

```
Score = 
0.30 × Coverage +
0.40 × Mutation Score +
0.20 × Pass Rate +
0.10 × Hallucination Bonus
```

---

### Final Decision

* PASS → Score ≥ 75
* FAIL → Otherwise

---

## 🧠 Example Insights

* High coverage ≠ correct tests
* Mutation testing ensures test strength
* Hallucinations indicate LLM test errors
* Per-function status helps pinpoint weak areas

---

## ⚠️ Notes

* Mutation testing runs **only when tests are clean**
* `target/` folder is auto-generated and ignored in Git
* Reports are generated locally after execution

---

## 📌 Future Improvements

* Automatic function detection from source code
* GitHub repo input support
* Dashboard UI for reports
* CI/CD integration

---

## 👨‍💻 Authors

Vanshika Bhalla & Harikrishnaa P M
