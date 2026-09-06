"""check-app-insights-config.py: the agent config file no other gate reads.

The expectations here are not taken from the Application Insights documentation.
Each one was established by running the pinned agent artifact (3.7.9, the version
docker/app/Dockerfile pins and SHA-256 verifies) against the case and recording what
it did, so a test that says "fails agent startup" means the agent was observed
failing startup on exactly that input.
"""

from conftest import run_tool, write

TOOL = "check-app-insights-config.py"

DOCKERFILE = (
    "FROM tomcat:11-jre21\n"
    "ARG APPLICATIONINSIGHTS_VERSION=3.7.9\n"
    "ARG APPLICATIONINSIGHTS_SHA256=4ab7a442bf9defc7475d026c6b49042793ebb010c391a1db91ae07da0c04d848\n"
    "COPY ./docker/app/applicationinsights.json /opt/applicationinsights.json\n"
)

# The shape the repository actually ships (PR #1906).
VALID = """{
  "role": { "name": "simis-cms" },
  "sampling": {
    "percentage": 100,
    "overrides": [
      { "telemetryType": "dependency", "percentage": 5 }
    ]
  },
  "selfDiagnostics": { "level": "warn", "destination": "console" }
}
"""


def seed(repo, config: str, dockerfile: str = DOCKERFILE):
    write(repo, "docker/app/applicationinsights.json", config)
    write(repo, "docker/app/Dockerfile", dockerfile)


def sampling(overrides: str, percentage: str = '"percentage": 100,') -> str:
    return '{"sampling": {%s "overrides": [%s]}}' % (percentage, overrides)


def test_shipped_config_passes_strict(repo):
    seed(repo, VALID)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_uppercase_telemetry_type_fails_strict(repo):
    """The trap this check exists for. Configuration$SamplingTelemetryType declares
    its constants as REQUEST/DEPENDENCY/TRACE/EXCEPTION, so reading the enum out of
    the jar with javap suggests uppercase is the value to write -- PR #1906 recorded
    its schema verification that way. The agent accepts only the lowercase tokens and
    fails startup on "DEPENDENCY":

        Cannot deserialize value of type ...Configuration$SamplingTelemetryType from
        String "DEPENDENCY": not one of the values accepted for Enum class:
        [dependency, trace, exception, request]

    Startup failure does not stop the JVM, so this ships as a running site with no
    telemetry at all."""
    seed(repo, sampling('{"telemetryType": "DEPENDENCY", "percentage": 5}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    out = r.stdout + r.stderr
    assert "DEPENDENCY" in out
    # The finding must name the fix, not just reject the value.
    assert 'lowercase "dependency"' in out


def test_misspelled_telemetry_type_fails_strict(repo):
    seed(repo, sampling('{"telemetryType": "dependancy", "percentage": 5}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "dependancy" in (r.stdout + r.stderr)


def test_all_four_accepted_types_pass_strict(repo):
    seed(repo, sampling(
        '{"telemetryType": "request", "percentage": 1},'
        '{"telemetryType": "dependency", "percentage": 2},'
        '{"telemetryType": "trace", "percentage": 3},'
        '{"telemetryType": "exception", "percentage": 4}'
    ))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_misspelled_override_key_fails_strict(repo):
    """The quietest failure: the agent logs one WARN, starts "successfully", and
    drops the property. Nothing downstream says the override is not in effect."""
    seed(repo, sampling('{"telemetryTpye": "dependency", "percentage": 5}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "telemetryTpye" in (r.stdout + r.stderr)


def test_misspelled_sampling_key_fails_strict(repo):
    """Same silent shape one level up: "overides" leaves sampling at 100% -- the
    override block is simply not there, and the bill never moves."""
    seed(repo, '{"sampling": {"percentage": 100, "overides": '
               '[{"telemetryType": "dependency", "percentage": 5}]}}')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "overides" in (r.stdout + r.stderr)


def test_override_without_percentage_fails_strict(repo):
    """FriendlyException at startup: "A sampling override configuration is missing
    a percentage"."""
    seed(repo, sampling('{"telemetryType": "dependency"}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "percentage" in (r.stdout + r.stderr)


def test_negative_percentage_fails_strict(repo):
    seed(repo, sampling('{"telemetryType": "dependency", "percentage": -1}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1


def test_percentage_above_100_fails_strict(repo):
    """Not a startup failure -- the agent rounds it down to 100 with a WARN. A
    "percentage": 1000 typo therefore means "keep everything", the opposite of the
    intent, and costs money silently."""
    seed(repo, sampling('{"telemetryType": "dependency", "percentage": 1000}'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1


def test_malformed_json_fails_strict_and_names_the_line(repo):
    seed(repo, '{\n  "sampling": {\n    "percentage": 100,\n  }\n}\n')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    out = r.stdout + r.stderr
    assert "MALFORMED" in out
    assert "line 4" in out


def test_optional_override_properties_are_not_flagged(repo):
    """id/attributes/spanKind are real SamplingOverride properties. A check that
    only knew about telemetryType and percentage would reject valid config."""
    seed(repo, sampling(
        '{"telemetryType": "dependency", "percentage": 5, "id": "deps",'
        ' "attributes": [{"key": "http.url", "value": "/health", "matchType": "strict"}]}'
    ))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_other_sampling_properties_are_not_flagged(repo):
    seed(repo, '{"sampling": {"requestsPerSecond": 5, "limitPerSecond": 10}}')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_non_sampling_blocks_are_not_schema_checked(repo):
    """Scope boundary: only the sampling block's schema was verified against the
    artifact, so everything else is checked for JSON validity alone. Asserting a
    guessed schema over the rest of the agent's surface would fail valid config."""
    seed(repo, '{"role": {"name": "simis-cms", "instance": "x"},'
               ' "preview": {"anything": true},'
               ' "sampling": {"percentage": 100}}')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_config_without_sampling_block_passes(repo):
    seed(repo, '{"role": {"name": "simis-cms"}}')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_agent_version_bump_is_flagged(repo):
    """The accepted values and property names were read out of one specific agent
    release. A bump has to re-verify them rather than inherit an assertion nobody
    re-checked, so drift between the Dockerfile pin and VERIFIED_AGENT_VERSION is
    itself a finding."""
    seed(repo, VALID, DOCKERFILE.replace("3.7.9", "3.8.0"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    out = r.stdout + r.stderr
    assert "3.8.0" in out and "3.7.9" in out


def test_unreadable_dockerfile_version_is_flagged(repo):
    seed(repo, VALID, "FROM tomcat:11-jre21\n")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "APPLICATIONINSIGHTS_VERSION" in (r.stdout + r.stderr)


def test_report_only_is_the_default(repo):
    """Every tool here defaults to reporting and exits 0; only --strict fails."""
    seed(repo, sampling('{"telemetryType": "DEPENDENCY", "percentage": 5}'))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "DEPENDENCY" in r.stdout


def test_missing_config_file_is_a_usage_error(repo):
    """A check that silently skips a moved or renamed file is worse than no check."""
    write(repo, "docker/app/Dockerfile", DOCKERFILE)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "not found" in (r.stdout + r.stderr)
