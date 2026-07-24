# Verifying SimIS CMS artifacts

Every published artifact carries verifiable, keyless (Sigstore) evidence of where
it came from and what is inside it. This page lists the exact commands. All of
them work with only the `gh` CLI (and `cosign` for the release SBOM blobs) — no
keys to distribute, nothing to trust but the transparency log.

## Container images (GHCR)

Both images publish **build provenance** (who built it, from which commit) and a
**CycloneDX SBOM attestation** (what is inside it), bound to the image digest:

```
gh attestation verify oci://ghcr.io/simisrnd/simis-cms:latest --owner SimisRnD
gh attestation verify oci://ghcr.io/simisrnd/simis-cms-db:latest --owner SimisRnD
```

That verifies every attestation on the digest. To select one kind:

```
# provenance only
gh attestation verify oci://ghcr.io/simisrnd/simis-cms:latest --owner SimisRnD \
  --predicate-type https://slsa.dev/provenance/v1
# SBOM only
gh attestation verify oci://ghcr.io/simisrnd/simis-cms:latest --owner SimisRnD \
  --predicate-type https://cyclonedx.org/bom
```

Pin to a digest rather than a tag when verifying what you actually deployed:
`oci://ghcr.io/simisrnd/simis-cms@sha256:<digest>`.

## The WAR (release asset)

Each release attaches the built `simis-cms.war` with its own build-provenance
attestation. Download the asset, then:

```
gh release download <tag> --repo SimisRnD/simis-cms --pattern 'simis-cms.war'
gh attestation verify simis-cms.war --owner SimisRnD
```

The attestation binds the file's SHA-256, so any modification — or a locally
rebuilt WAR, which is not byte-identical — fails verification. That is the point.

## The release SBOM (signed blobs)

Releases also attach a CycloneDX SBOM generated from the shipped WAR, signed
keylessly with cosign. Each SBOM (`bom.json` / `bom.xml`) travels with a
Sigstore bundle (`bom.json.sigstore.json` / `bom.xml.sigstore.json`) that
carries the signature, certificate, and transparency-log entry together:

```
cosign verify-blob bom.json \
  --bundle bom.json.sigstore.json \
  --certificate-identity-regexp '^https://github.com/SimisRnD/simis-cms/' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

## Air-gapped verification

Fetch the attestation bundle online, verify offline:

```
# online side
gh attestation download oci://ghcr.io/simisrnd/simis-cms:latest --owner SimisRnD
gh attestation trusted-root > trusted_root.jsonl
# offline side (bundle + trusted root + artifact travel together)
gh attestation verify oci://... --owner SimisRnD \
  --bundle <digest>.jsonl --custom-trusted-root trusted_root.jsonl
```

## What else is pinned or published

- **Base images** are digest-pinned in the Dockerfiles (`FROM name:tag@sha256:…`),
  and every workflow action is pinned to a commit SHA; Dependabot maintains both.
- **Vulnerability posture** for the DB image is published as OpenVEX at
  `docker/db/vex/simis-cms-db.openvex.json` — machine-enforced by the publish
  gate, so a new CRITICAL/HIGH cannot reach `:latest` without a recorded triage
  decision. Statements carry per-CVE justifications and the configuration
  caveats under which they hold.
- Published images are scanned on a monthly schedule as well as on every publish;
  results land in the repository's Security tab.
