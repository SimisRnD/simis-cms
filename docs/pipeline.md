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

## Security Audit Cadence

Security is enforced at multiple layers, from per-commit automation through
periodic human review. The table below summarises what runs when and who owns it.

| Frequency | What | Owner |
|-----------|------|-------|
| Every push / PR | CodeQL SAST, `unescaped-el` XSS gate, Trivy image scan, keyword security labeler | CI (automatic) |
| Per PR | Hand-review any PR carrying the `security` label for auth, session, permissions, or crypto changes | Reviewer |
| Weekly (Monday) | Cloud agent scans open security PRs and issues; files a GitHub issue if any item has had no activity in 14+ days | Automated routine |
| Quarterly | Audit open security issue backlog; re-triage VEX items for CVEs that have gained exploitability evidence; check `provided`-scope dependencies (e.g. `tomcat-servlet-api`) that Dependabot skips | Maintainer |
| Annually / on major changes | Full threat-model review after new auth flows, infra changes, or external integrations; penetration test for customer-facing deployments | Maintainer |
| Event-driven | Triage any CVE in a direct dependency within the current sprint; full security sweep before a major release cut | Maintainer |

### Per-PR security review checklist

When a PR carries the `security` label, reviewers should verify:

- Authentication and session changes do not weaken existing controls (step-up gates, MFA, cookie flags).
- Authorization checks are deny-by-default; new admin-layout pages go through the access gate (`completeness` CI check enforces this).
- User-supplied input that reaches HTML output is HTML-encoded; EL expressions are escaped (`unescaped-el` CI check enforces this for JSPs).
- Cryptographic operations use approved algorithms (Argon2 for passwords; AES-GCM for data at rest).
- No secrets, tokens, or credentials are logged or returned in API responses.
- New tests cover the security-critical path; `security` label PRs without tests should be flagged for follow-up.

### Quarterly dependency audit

Dependabot covers compile- and test-scope dependencies. `provided`-scope
libraries (those supplied by the runtime container) are excluded because they
are not vendored into the WAR. Run the drift check manually and cross-reference
against the current NVD feed:

```bash
python3 tools/check-dependency-drift.py
```

For `provided`-scope libraries (currently `tomcat-servlet-api`), track the
Tomcat release notes directly and update the hold rationale in
`.github/dependabot.yml` when the servlet API version changes.

### VEX triage

Trivy findings that are assessed as `not_affected` are recorded in
`docker/app/vex.json`. During each quarterly review, re-open any item whose
`justification` may no longer hold (e.g. a previously unexploitable CVE now
has a public PoC) and either patch or re-justify with updated evidence.
