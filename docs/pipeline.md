---
id: pipeline
title: DevSecOps Pipeline
# prettier-ignore
description: SimIS CMS is DevSecOps friendly
---

## CI/CD Pipeline

### Pipeline Code Dependencies Scan

```bash
npm install -g snyk
snyk auth
snyk test --all-projects
```

### Pipeline Linting Stage

```bash
ant checkstyle
```

### Pipeline Build Stage

```bash
ant compile
```

### Pipeline Unit Tests and Coverage

```bash
ant test
```

### Pipeline Containerization

```bash
docker build . -t cms-platform
```
