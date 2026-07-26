# Security Policy

## Reporting a Vulnerability

Please do **not** open a public issue for security vulnerabilities.

Report privately using GitHub's **"Report a vulnerability"** button under this repository's **Security** tab (Security → Advisories → Report a vulnerability).
You can also send us a message at https://simisinc.com/contact-us

Please include: affected version/commit, a description and steps to reproduce.

## What to expect

- We aim to acknowledge reports within 5 business days.
- We will keep you updated as we investigate and, where appropriate, coordinate a fix and disclosure timeline with you.
- We ask that you give us a reasonable opportunity to remediate before any public disclosure.

## Supported Versions

Security fixes are applied to the 'main' branch. We do not maintain patched builds of older versions.

## Audit Cadence

Security checks run at multiple frequencies:

- **Every PR** — CodeQL, Trivy, XSS gate, and a keyword labeler that applies
  the `security` label automatically; labelled PRs receive an extra reviewer pass.
- **Weekly** — An automated agent scans open security PRs and issues and files
  a staleness alert if any item has had no activity in 14 days.
- **Quarterly** — Maintainers review the open security backlog, re-triage VEX
  items, and audit `provided`-scope dependencies that Dependabot skips.
- **Annually / on major changes** — Threat-model review and, for
  customer-facing deployments, a penetration test.

See `docs/pipeline.md` for the full DevSecOps pipeline and per-PR checklist.

## Scope

This policy covers the code in this repository. It does **not** cover any
third-party dependencies (report those to their upstream projects) or any
hosted deployment infrastructure.
