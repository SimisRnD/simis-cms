"""Shared fixtures for the tools/*.py test suite.

Each check tool is exercised through its real CLI (subprocess against a
synthetic repository tree in tmp_path), so the tests cover argument parsing and
exit codes -- the contract CI actually depends on -- not just internals.
"""

import subprocess
import sys
from pathlib import Path

import pytest

TOOLS_DIR = Path(__file__).resolve().parent.parent

POM_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.simisinc</groupId>
  <artifactId>simis-cms</artifactId>
  <version>{version}</version>
  <dependencies>
{dependencies}
  </dependencies>
</project>
"""

DEP_TEMPLATE = """    <dependency>
      <groupId>{group}</groupId>
      <artifactId>{artifact}</artifactId>
      <version>{version}</version>
    </dependency>
"""


def run_tool(name: str, root: Path, *args: str) -> subprocess.CompletedProcess:
    """Run tools/<name>.py against a repo root; returns the completed process."""
    return subprocess.run(
        [sys.executable, str(TOOLS_DIR / name), str(root), *args],
        capture_output=True, text=True,
    )


def write(root: Path, rel: str, content: str) -> Path:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    return p


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    """A minimal synthetic repository tree the tools can run against."""
    return tmp_path


def make_pom(root: Path, version: str = "1.0.0-SNAPSHOT", deps=None) -> None:
    dep_xml = "".join(
        DEP_TEMPLATE.format(group=g, artifact=a, version=v) for g, a, v in (deps or [])
    )
    write(root, "pom.xml", POM_TEMPLATE.format(version=version, dependencies=dep_xml))


def make_application_info(root: Path, version: str = "1.0.0") -> None:
    write(
        root,
        "src/main/java/com/simisinc/platform/ApplicationInfo.java",
        'package com.simisinc.platform;\n\n'
        'public class ApplicationInfo {\n'
        f'  public static final String VERSION = "{version}";\n'
        '}\n',
    )
