"""rolling-deploy.sh: the staging slot's identity, its running state, and loud failures.

The script only ever talks to Azure through `az`, so each test puts a fake `az` first on
PATH. The fake answers from a JSON state file and records every call, which lets the
tests assert on what the script did -- and, as importantly, on what it did NOT do:
when the staging slot's identity lacks production's role grants, nothing may be
deployed, started, or swapped.

The behavior pinned here comes from a real failure (2026-10-21). The staging slot had
no managed identity, so after each swap it could neither pull its image nor resolve its
Key Vault references, and it failed to log in to the database every seven seconds for
days while deploys kept reporting success.
"""

import json
import os
import stat
import subprocess
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parent.parent / "rolling-deploy.sh"

SUB = "/subscriptions/00000000-0000-0000-0000-000000000001"
RG_ID = f"{SUB}/resourceGroups/rg-app"
APP_ID = f"{RG_ID}/providers/Microsoft.Web/sites/app-test"
ACR_SCOPE = f"{RG_ID}/providers/Microsoft.ContainerRegistry/registries/crtest"
KV_SCOPE = f"{RG_ID}/providers/Microsoft.KeyVault/vaults/kv-test"
ACR_PULL = f"{SUB}/providers/Microsoft.Authorization/roleDefinitions/7f951dda-4ed3-4680-a7ca-43fe172d538d"
KV_SECRETS_USER = f"{SUB}/providers/Microsoft.Authorization/roleDefinitions/4633458b-17de-408a-b874-0445c86b69e6"

PROD = "11111111-1111-1111-1111-111111111111"
STAGING = "22222222-2222-2222-2222-222222222222"
NEW_STAGING = "33333333-3333-3333-3333-333333333333"

FULL_ACCESS = [[ACR_PULL, ACR_SCOPE], [KV_SECRETS_USER, KV_SCOPE]]

# A stand-in for the Azure CLI. It recognizes exactly the commands the script runs and
# fails loudly on anything else, so a new az call cannot slip past these tests unmodelled.
FAKE_AZ = r'''#!/usr/bin/env python3
import json, os, re, sys

args = sys.argv[1:]
state_path = os.environ["FAKE_AZ_STATE"]
with open(state_path) as f:
    state = json.load(f)
with open(os.environ["FAKE_AZ_LOG"], "a") as f:
    f.write(json.dumps(args) + "\n")

words = []
for a in args:
    if a.startswith("-"):
        break
    words.append(a)
command = " ".join(words)

def opt(name):
    return args[args.index(name) + 1] if name in args else None

if command in state.get("fail", []):
    print(f"ERROR: simulated failure of az {command}", file=sys.stderr)
    sys.exit(1)

on_slot = opt("--slot") == "staging"

if command == "webapp deployment slot list":
    print("\n".join(state["slots"]))
elif command == "webapp deployment slot create":
    state["slots"].append("staging")
elif command == "webapp show":
    query = opt("--query")
    print({"virtualNetworkSubnetId": state.get("subnet_id", ""),
           "id": state["app_id"],
           "defaultHostName": "app-test-staging.azurewebsites.net"}[query])
elif command == "webapp vnet-integration add":
    pass
elif command == "webapp identity show":
    principal = state["staging_principal"] if on_slot else state["production_principal"]
    if principal:
        print(principal)
elif command == "webapp identity assign":
    state["staging_principal"] = state["assigned_principal"]
    print(state["assigned_principal"])
elif command == "rest":
    principal = re.search(r"assignedTo\('([^']*)'\)", opt("--url")).group(1)
    for role, scope in state["grants"].get(principal, []):
        print(f"{role} {scope}")
elif command in ("webapp config container set", "webapp start", "webapp stop",
                 "webapp deployment slot swap"):
    pass
else:
    print(f"fake az: unmodelled command: {args}", file=sys.stderr)
    sys.exit(99)

with open(state_path, "w") as f:
    json.dump(state, f)
'''


@pytest.fixture
def azure(tmp_path):
    """Builds the fake az and returns a runner: run(**state overrides) -> (result, calls)."""
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    az = bin_dir / "az"
    az.write_text(FAKE_AZ)
    az.chmod(az.stat().st_mode | stat.S_IXUSR)

    def run(**overrides):
        state = {
            "slots": ["staging"],
            "subnet_id": "",
            "app_id": APP_ID,
            "production_principal": PROD,
            "staging_principal": STAGING,
            "assigned_principal": NEW_STAGING,
            "grants": {PROD: FULL_ACCESS, STAGING: FULL_ACCESS},
            "fail": [],
        }
        state.update(overrides)
        state_file = tmp_path / "state.json"
        state_file.write_text(json.dumps(state))
        log_file = tmp_path / "calls.jsonl"
        log_file.write_text("")
        env = dict(os.environ,
                   PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                   FAKE_AZ_STATE=str(state_file),
                   FAKE_AZ_LOG=str(log_file))
        result = subprocess.run(
            ["bash", str(SCRIPT),
             "--resource-group", "rg-app",
             "--app-service-name", "app-test",
             "--image-uri", "crtest.azurecr.io/simis-cms:abc123",
             "--health-check-timeout", "0",
             "--drain-timeout", "0"],
            capture_output=True, text=True, env=env,
        )
        calls = [json.loads(line) for line in log_file.read_text().splitlines()]
        return result, calls

    return run


def command_of(call):
    """The az sub-command words of one recorded call, plus its --slot if any."""
    words = []
    for a in call:
        if a.startswith("-"):
            break
        words.append(a)
    if "--slot" in call:
        words.append(f"[{call[call.index('--slot') + 1]}]")
    return " ".join(words)


def sequence(calls):
    return [command_of(c) for c in calls]


def test_deploy_starts_staging_after_the_new_image_and_stops_it_after_the_swap(azure):
    result, calls = azure()
    assert result.returncode == 0, result.stderr
    seq = sequence(calls)
    image_set = seq.index("webapp config container set [staging]")
    started = seq.index("webapp start [staging]")
    swapped = seq.index("webapp deployment slot swap [staging]")
    stopped = seq.index("webapp stop [staging]")
    # Started only once it holds the new image, so the first container it runs is the
    # new build; stopped only after the swap, so the previous build is what it keeps.
    assert image_set < started < swapped < stopped
    assert "holds all 2 of production's role grants" in result.stderr


def test_slot_without_an_identity_gets_one_but_nothing_deploys_until_it_is_granted_access(azure):
    """The incident's state: no identity at all on the slot."""
    result, calls = azure(staging_principal="")
    assert result.returncode != 0
    seq = sequence(calls)
    assert "webapp identity assign [staging]" in seq
    # Stopped before changing anything that matters: no image, no start, no swap.
    for forbidden in ("webapp config container set [staging]", "webapp start [staging]",
                      "webapp deployment slot swap [staging]"):
        assert forbidden not in seq
    # The administrator is handed the exact grants, for the NEW identity.
    assert (f"az role assignment create --assignee-object-id {NEW_STAGING} "
            f"--assignee-principal-type ServicePrincipal "
            f"--role 7f951dda-4ed3-4680-a7ca-43fe172d538d --scope {ACR_SCOPE}") in result.stderr
    assert f"--role 4633458b-17de-408a-b874-0445c86b69e6 --scope {KV_SCOPE}" in result.stderr
    assert "production is untouched" in result.stderr


def test_existing_identity_is_reused_not_replaced(azure):
    result, calls = azure()
    assert result.returncode == 0, result.stderr
    assert "webapp identity assign [staging]" not in sequence(calls)


def test_only_the_missing_grant_is_reported(azure):
    result, calls = azure(grants={PROD: FULL_ACCESS, STAGING: [[ACR_PULL, ACR_SCOPE]]})
    assert result.returncode != 0
    assert f"--scope {KV_SCOPE}" in result.stderr
    assert f"--scope {ACR_SCOPE}" not in result.stderr
    assert "webapp deployment slot swap [staging]" not in sequence(calls)


def test_grants_match_regardless_of_the_case_azure_returns_them_in(azure):
    shouted = [[role.upper(), scope.upper()] for role, scope in FULL_ACCESS]
    result, _ = azure(grants={PROD: FULL_ACCESS, STAGING: shouted})
    assert result.returncode == 0, result.stderr


def test_production_without_an_identity_is_an_error(azure):
    result, calls = azure(production_principal="")
    assert result.returncode != 0
    assert "Production has no system-assigned managed identity" in result.stderr
    assert "webapp deployment slot swap [staging]" not in sequence(calls)


def test_a_failed_grant_listing_is_an_error_not_an_empty_list(azure):
    result, calls = azure(fail=["rest"])
    assert result.returncode != 0
    assert "Could not list production's role assignments" in result.stderr
    assert "webapp config container set [staging]" not in sequence(calls)


def test_a_failed_slot_listing_is_an_error_not_an_empty_list(azure):
    """It used to read a failed listing as "no slots" and go on to create one."""
    result, calls = azure(fail=["webapp deployment slot list"])
    assert result.returncode != 0
    assert "Could not list the App Service's deployment slots" in result.stderr
    assert "webapp deployment slot create [staging]" not in sequence(calls)


def test_a_missing_slot_is_created(azure):
    result, calls = azure(slots=[])
    assert result.returncode == 0, result.stderr
    assert "webapp deployment slot create [staging]" in sequence(calls)


def test_failed_swap_stops_the_slot_and_fails(azure):
    result, calls = azure(fail=["webapp deployment slot swap"])
    assert result.returncode != 0
    seq = sequence(calls)
    assert seq.index("webapp deployment slot swap [staging]") < seq.index("webapp stop [staging]")
    assert "production untouched" in result.stderr


def test_failing_to_stop_after_a_successful_swap_warns_but_succeeds(azure):
    """The new build is already live; failing the job would misreport the deploy."""
    result, _ = azure(fail=["webapp stop"])
    assert result.returncode == 0, result.stderr
    assert "::warning::Could not stop the staging slot" in result.stdout
