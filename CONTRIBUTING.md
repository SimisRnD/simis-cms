# Contributing to SimIS CMS

Thanks for your interest in SimIS CMS. This page covers how to build the project, how changes are organized, and what a pull request needs before it can merge.

Questions and ideas are welcome in [Discussions](https://github.com/SimisRnD/simis-cms/discussions); bug reports in [Issues](https://github.com/SimisRnD/simis-cms/issues). Please report security vulnerabilities privately — see [SECURITY.md](SECURITY.md), and never open a public issue for one.

## Security and auditability

SimIS CMS is built for environments where security and accountability matter, so we hold to a high bar — security, auditability, and doing right by the product. Every change runs through automated, deterministic checks that leave an audit trail: build and tests, CodeQL static analysis, dependency and secret scanning, and a signed software bill of materials (SBOM) and build-provenance attestation at each release.

Development is **AI-assisted** (Claude Code, Anthropic) — an advisory tool that helps draft, review, and document changes so a small team can hold that bar. It doesn't replace judgment: every change is reviewed and merged by a SimIS maintainer, who remains accountable.

## Development setup

You'll need:

- **OpenJDK 21+** — the build targets Java 21 (`build.xml` sets `jdk=21`)
- **Apache Ant 1.10+** — Ant is the authoritative build; the Maven `pom.xml` supports IDE tooling and SBOM generation but does not produce the production artifact
- **PostgreSQL with PostGIS** — or use the database container: `docker build -f docker/db/Dockerfile .`

See [docs/developer-environment.md](docs/developer-environment.md) for the full IDE walkthrough and [docs/installation.md](docs/installation.md) for deployment.

## Build and test

```sh
ant clean compile                  # compile the application
ant -lib lib/tests ci-test         # run the full test suite (what CI runs)
ant -lib lib/war package           # build the production .war
```

Two things worth knowing:

- **Run `ant clean` before trusting a build.** Stale classes in `build/` can produce misleading errors (phantom "cannot find symbol", tests that no longer exist still running).
- **Dependencies are vendored** in `lib/`. If you change a library version, update **both** the jar in `lib/` and the version in `pom.xml` — they can silently drift apart otherwise.

## Making changes

- **Branch from `main`**, named by intent: `security/...`, `fix/...`, `feature/...`, `docs/...`.
- **One concern per pull request.** Small, reviewable PRs merge quickly; mixed ones stall.
- **Write tests** for behavior you add or change. The suite runs on every PR and must pass.
- **Match the surrounding code** — this codebase favors explicit, readable Java; follow the style of the file you're editing.
- **Database changes** follow the dual-track Flyway convention: fresh installs run the `NEW_` script and baseline; existing installs run `UPGRADE_` scripts. An upgrade script must be **idempotent** (`IF NOT EXISTS` guards) whenever the fresh-install script already creates the same objects.

## Pull request expectations

A PR is ready to merge when:

1. **CI is green** — build, test suite, and CodeQL analysis all pass.
2. **It's labeled** with what it touches: `security`, `bug`, `dependencies`, `ci`, `documentation`, `authentication`, `modernization`, or `enhancement`.
3. **The description says what and why** — what problem it solves, how it was verified.

### Stacked pull requests

Prefer independent PRs based on `main`. If a PR must build on another open PR, base it on that branch, apply the red **`stacked: merge base PR first`** label, and state the merge order in the description. After a stacked chain merges, verify each PR's changes actually reached `main`:

```sh
git merge-base --is-ancestor <merge-commit-sha> origin/main && echo landed
```

A "Merged" badge alone is not proof — an out-of-order stack merge can strand a PR's content on an intermediate branch.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Be excellent to each other.
