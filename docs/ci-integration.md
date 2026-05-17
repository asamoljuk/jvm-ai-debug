# CI Integration

## GitHub Actions workflow

The workflow file is located at `.github/workflows/build.yml`. It triggers on every push and pull request to every branch.

### Permissions

The workflow requires these GitHub token permissions:

```yaml
permissions:
  pull-requests: write   # to post PR comments
  contents: read         # to check out source
```

### Java version

The workflow uses Java **21** (Eclipse Temurin) via `actions/setup-java@v4`. The source is compiled at `source/target 17` so it is compatible with both the local JDK 17 and the CI JDK 21.

### Steps

The workflow runs in this order:

#### 1. Checkout
Standard `actions/checkout@v4`.

#### 2. Build tool JAR (`mvn package -DskipTests`)
Builds `target/jvm-ai-debug.jar` without running tests. This step uses `continue-on-error: true` — if the source code itself fails to compile, subsequent steps handle it gracefully rather than aborting the workflow.

The reason this step runs first (before the full `verify`) is to ensure the analyzer JAR is available for the failure analysis step even when tests fail.

#### 3. Run full build and tests (`mvn verify`)
Runs `mvn --batch-mode --no-transfer-progress clean verify`. Output is piped through `tee` to both the console and `build-output.txt`:

```bash
mvn --batch-mode --no-transfer-progress clean verify 2>&1 | tee build-output.txt
```

This step uses `continue-on-error: true` so the workflow continues to the analysis step on failure. The Maven exit code is captured via `${PIPESTATUS[0]}` and stored as a step output (`exit_code`).

#### 4. Analyze failure (conditional: only when step 3 failed)
If `target/jvm-ai-debug.jar` exists, runs:

```bash
java -jar target/jvm-ai-debug.jar analyze build-output.txt \
  --provider mock --format json > analysis.json
```

Sets step output `analysis_available=true` if the JAR exists and the analysis ran, or `analysis_available=false` if the JAR was not built (compilation failure). In the latter case the raw `build-output.txt` is printed to the workflow log.

This step uses `continue-on-error: true` — a failure here (e.g., malformed output file) does not prevent the workflow from propagating the original build failure.

#### 5. Post PR comment (conditional: only on `pull_request` events with analysis available)
Uses `actions/github-script@v7` to read `analysis.json` and create a pull request comment via the GitHub REST API.

The comment format:

```markdown
## 🔍 AI JVM Debug Assistant

> **Spring application context failed to start** · Confidence: 🔴 High

### Likely Root Cause
A circular dependency between Spring beans...

### Evidence
- BeanCreationException found in stack trace
- ...

### Suggested Fixes
1. Refactor the dependency direction...
2. ...

### Files / Classes Mentioned
`UserService`, `NotificationService`

---
<sub>Analyzed by **jvm-ai-debug** · pattern: `SPRING_CONTEXT_FAILURE` · provider: mock</sub>
```

Confidence levels are displayed with emoji badges:
- `HIGH` → 🔴 High
- `MEDIUM` → 🟡 Medium
- `LOW` → ⚪ Low

The `Files / Classes Mentioned` section is omitted if no classes were detected.

#### 6. Upload artifacts (conditional)
- On **failure**: uploads `build-output.txt` and `analysis.json` as artifact `build-output`
- On **success**: uploads `target/jvm-ai-debug.jar` as artifact `jvm-ai-debug-jar`

#### 7. Propagate build failure
If step 3 failed, exits with code `1` to mark the CI job as failed. This step runs last — after the comment has been posted — so the failure status does not prevent the comment from being created.

---

## Using JSON output in other CI systems

The `--format json` output can be used in any CI system that supports shell scripting.

### GitLab CI example

```yaml
analyze-failure:
  stage: analyze
  when: on_failure
  script:
    - java -jar jvm-ai-debug.jar analyze build.log --provider mock --format json > analysis.json
    - cat analysis.json
    - |
      CONFIDENCE=$(jq -r '.confidence' analysis.json)
      TITLE=$(jq -r '.title' analysis.json)
      echo "Diagnosis: $TITLE (confidence: $CONFIDENCE)"
  artifacts:
    paths:
      - analysis.json
    when: on_failure
```

### Jenkins pipeline example

```groovy
post {
    failure {
        script {
            sh 'java -jar jvm-ai-debug.jar analyze build.log --provider mock --format json > analysis.json'
            def analysis = readJSON file: 'analysis.json'
            echo "Detected: ${analysis.detectedIssue} (${analysis.confidence})"
            echo "Root cause: ${analysis.likelyRootCause}"
        }
    }
}
```

---

## Bootstrapping limitation

If the source code fails to compile entirely, `target/jvm-ai-debug.jar` will not exist and the analysis step is skipped. In this case the raw `build-output.txt` is printed to the workflow log and uploaded as an artifact for manual inspection.

To analyze compilation failures in a self-referential setup, you could download a pre-built release of the tool rather than relying on the in-repo JAR. This is not currently implemented but is achievable by replacing step 2 with a `curl` download from a GitHub release.

---

## Provider in CI

The workflow uses `--provider mock`. This means no API key is needed and the analysis is instant and deterministic.

To use a real provider in CI, set the API key as a GitHub Actions secret and pass `--provider openai` (or `anthropic`):

```yaml
- name: Analyze failure with jvm-ai-debug
  if: steps.verify.outcome == 'failure'
  env:
    OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  run: |
    java -jar target/jvm-ai-debug.jar analyze build-output.txt \
      --provider openai --format json > analysis.json
```
