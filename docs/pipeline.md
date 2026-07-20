---
id: pipeline
title: DevSecOps Pipeline
# prettier-ignore
description: SimIS CMS is DevSecOps friendly
---

## CI/CD Pipeline

Every change runs through automated, deterministic checks in GitHub Actions
(see `.github/workflows/`). Most stages can also be run locally with Ant.

### Static Analysis (SAST)

CodeQL scans the first-party code on every push and pull request
(`.github/workflows/codeql.yml`); findings appear under the repository's
**Security → Code scanning** tab.

### Dependency and Secret Scanning

Dependencies are kept current with Dependabot and checked for drift
(`.github/workflows/dependency-drift.yml`), and GitHub secret scanning guards
against committed credentials. A Snyk dependency scan can also be run locally:

```bash
npm install -g snyk
snyk auth
snyk test --all-projects
```

### Linting Stage

```bash
ant checkstyle
```

### Build Stage

```bash
ant clean compile
ant -lib lib/war compile-jsp   # JSP syntax gate: fails on a JSP that won't translate/compile
```

### Unit Tests and Coverage

```bash
ant test
```

CI additionally enforces a minimum test-coverage floor on security-critical
classes (`.github/scripts/check-security-coverage.sh`).

### Generate Web Application

```bash
ant -lib lib/war package
```

### Container Images, Image Scanning, and SBOM

The application and database images are built, scanned with Trivy, and published
with a signed CycloneDX SBOM and build-provenance attestation
(`.github/workflows/publish-images.yml` and `.github/workflows/sbom.yml`).
