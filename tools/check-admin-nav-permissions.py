#!/usr/bin/env python3
"""Report admin menu rows shown to someone who cannot open the page they link to.

Background
----------
The admin off-canvas menu in ``src/main/webapp/WEB-INF/jsp/main.jsp`` decides who sees
each row. The layout XML under ``src/main/webapp/WEB-INF/web-layouts/page/`` decides who
may open the page that row links to. Nothing connects the two, so they drift silently:
the menu row and the page declaration are edited in different files, usually in different
PRs, and a mismatch produces no error anywhere. The user simply sees a link, clicks it,
and is denied.

The drift is directional and only one direction matters. A row *narrower* than its page
is merely a hidden-but-reachable page -- a discoverability question, not a broken link.
A row *wider* than its page is a dead link, and that is what this checks.

This is a live hazard, not a hypothetical. Issue #1764 moves the CSP Violations row out
of Settings and into the Access section beside the Audit Log. Every other page in Access
is declared ``role="admin" capability="admin:manage"`` and the section is gated to match
(``hasRole('admin') || hasPermission('admin:manage')``), but ``/admin/csp-violations`` is
declared ``role="admin"`` only. Dropping the row into that ``<ul>`` the way every
neighbouring row is written would have shown a dead link to every admin:manage holder.
PR #1768 caught it by hand and wrapped that one row in its own ``hasRole('admin')`` test.
Nothing would have caught it if the author had not thought of it; this gate is what makes
that requirement enforced rather than remembered.

What it does
------------
Reads the ``<li>`` rows of the admin menu in main.jsp, computes each row's effective
visibility test (the AND of every ``<c:if>`` enclosing it, plus its own), resolves the
row's ``href`` to a ``<page>`` declaration in the layout XML, and compares who each side
admits.

The comparison is exact rather than syntactic. Both sides are evaluated over every subset
of the principal universe -- the roles and capabilities named anywhere in the menu or the
layout XML, plus the ``guest``/``users`` pseudo-roles. A subset that satisfies the row's
visibility test but not the page's declaration is a finding, reported with the specific
principal that would see the dead link. Enumerating subsets rather than comparing token
sets is what makes negation (``!userSession.hasRole('admin')``, used by the Editorial
Calendar de-duplication row) and mixed ``&&``/``||`` nesting come out right.

Page access mirrors ``WebComponentCommand.allowsUser(roles, groups, capabilities, ...)``:
a user is admitted by holding EITHER a listed role OR a listed capability, and a page
declaring neither is open to everyone. Href-to-page resolution mirrors
``WebPageXmlLayoutCommand.locatePage()``: exact match first, then progressively shorter
path prefixes, which is how ``/admin/documentation/wiki/Home`` resolves to the
``/admin/documentation/wiki`` declaration.

Conditions that are not about the user -- ``${pageRenderInfo.name eq '/admin'}``,
``${ecommercePropertyMap['ecommerce.enabled'] eq 'true'}`` -- are treated as free: the row
counts as visible if ANY value of them makes the test true. A section switched off by a
site property is still a dead link for the principals who see it once it is switched on.

Parsing is deliberately conservative. main.jsp is JSP with EL, not XML, and the same
``<c:if>`` tag is used two different ways in it: as a block that wraps rows, and inline
inside an ``<li>`` start tag to toggle ``class="is-active"``. Only a ``<c:if>`` alone on
its line opens a block; a line whose ``<c:if>`` tags balance is inline and ignored. A row
this script cannot fully account for -- an unrecognised predicate, an unbalanced tag, an
href it cannot resolve to a page -- is reported as UNDETERMINED and fails the build under
--strict. It never silently skips a row it did not understand.

The summary reports how many rows were actually checked, because a static checker's worst
failure is a silent one. tools/check-column-length-limits.py reported a clean run while its
column regex matched nothing at all; it now prints the number of limits it checked, and this
gate prints its row count for the same reason. A run that finds no rows is itself a failure
-- a parser that has stopped matching must not be able to look like a pass.

Modes
-----
Default is REPORT-ONLY: it prints findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 on a dead-link row, an undetermined row, or a run that parsed no
rows at all.

Exit codes: 0 = clean (or report-only), 1 = a problem found under --strict, 2 = bad usage,
or main.jsp / the layout directory is missing.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import glob
import itertools
import os
import re
import sys
import xml.etree.ElementTree as ET

MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
LAYOUT_GLOB = "src/main/webapp/WEB-INF/web-layouts/page/*.xml"

# The admin off-canvas menu is the region this checks. Rows outside it (the public site
# header, the user dropdown) are not admin navigation and are not scanned.
MENU_START = re.compile(r'id="offCanvas"')
MENU_END = re.compile(r"</nav>")

# ${userSession.hasRole('admin')} / ${userSession.hasPermission('admin:manage')}
HAS_ROLE = re.compile(r"userSession\s*\.\s*hasRole\(\s*'([^']*)'\s*\)")
HAS_PERMISSION = re.compile(r"userSession\s*\.\s*hasPermission\(\s*'([^']*)'\s*\)")

# A block-opening <c:if> is alone on its line. Anything else containing <c:if> must have
# its tags balanced on that line (the inline is-active toggle) or we cannot read the file.
BLOCK_IF = re.compile(r'^<c:if\s+test="([^"]*)"\s*>$')
CIF_OPEN = re.compile(r"<c:if\b")
CIF_CLOSE = re.compile(r"</c:if>")

# <a href="${ctx}/admin/audit-log"> -- the row's target.
HREF = re.compile(r'<a\s+href="\$\{ctx\}(/[^"]*)"')

# Pseudo-roles WebComponentCommand honours on the page side; always part of the universe
# so a page declaring role="users" is modelled, even though the menu never tests for them.
PSEUDO_ROLES = ("guest", "users")

# href -> the principals already known to be shown this dead link. Keyed by href rather
# than line number because main.jsp's line numbers move whenever any row is added.
#
# Baseline captured 2026-09-01 against upstream/main. These three rows are the whole of
# the un-gated first <ul> ("Admin": Welcome, Documentation, Activity), which carries no
# <c:if> at all and so is shown to everyone who can render any admin page -- including
# principals its own pages do not admit. A data-manager opens /admin/collections
# (role="admin,data-manager"), sees "Welcome", and is denied at /admin
# (role="admin,content-manager,community-manager,ecommerce-manager"). An admin:manage
# holder reaches /admin/apis the same way and is denied the same three rows -- the
# capability side of issue #733, which broadened the Community section's gate but not
# this one.
#
# They are recorded, not fixed, because the fix is an authorization decision with two
# defensible answers (narrow the rows with a <c:if>, or widen the three pages to admit
# the capability), and this PR adds the detector rather than choosing. Remove an entry
# when its row is fixed; never add one without saying why here. A new href, or a wider
# leak on one of these, fails --strict.
ALLOWLIST: dict[str, set] = {
    "/admin": {"capability:admin:manage", "capability:users:manage", "role:data-manager"},
    "/admin/documentation/wiki/Home": {"capability:admin:manage", "capability:users:manage",
                                       "role:data-manager", "role:ecommerce-manager"},
    "/admin/activity": {"capability:admin:manage", "capability:users:manage",
                        "role:data-manager"},
}


class Undetermined(Exception):
    """The parser could not account for something; the row is reported, never skipped."""


# --------------------------------------------------------------------------- expressions

class Expr:
    """A visibility test reduced to the principal predicates it contains.

    ``eval`` answers: with this exact set of held roles/capabilities, and with the
    non-principal predicates free to take any value, can this test be true?
    """

    def __init__(self, kind, *args):
        self.kind = kind
        self.args = args

    def evaluate(self, held: frozenset) -> bool:
        if self.kind == "role":
            return ("role", self.args[0]) in held
        if self.kind == "perm":
            return ("perm", self.args[0]) in held
        if self.kind == "free":
            # Not about the user. Free to be either value, so it never constrains
            # visibility in one direction -- see FREE_HIGH/FREE_LOW below.
            return self.args[0]
        if self.kind == "not":
            return not self.args[0].evaluate(held)
        if self.kind == "and":
            return all(a.evaluate(held) for a in self.args)
        if self.kind == "or":
            return any(a.evaluate(held) for a in self.args)
        raise Undetermined("unknown expression node: %s" % self.kind)


def _free_variants(expr: Expr):
    """Yield the expression with every free predicate pinned true, and pinned false.

    A test mixing a principal predicate with a site-property predicate is visible if ANY
    setting of the property makes it true, and negation means neither extreme is enough on
    its own. Two evaluations bound it: main.jsp's free predicates are never negated
    individually, so the true-pinned and false-pinned readings cover the reachable range.
    """

    def pin(node: Expr, value: bool) -> Expr:
        if node.kind == "free":
            return Expr("free", value)
        if node.kind in ("not", "and", "or"):
            return Expr(node.kind, *[pin(a, value) for a in node.args])
        return node

    return pin(expr, True), pin(expr, False)


def visible_to(expr: Expr, held: frozenset) -> bool:
    """True if some setting of the non-principal predicates shows this row to `held`."""
    return any(v.evaluate(held) for v in _free_variants(expr))


def parse_el(text: str) -> Expr:
    """Parse one c:if test= EL expression into an Expr.

    Handles the shapes main.jsp actually uses: ${...} wrapping, &&/||/! with parentheses,
    hasRole/hasPermission calls, and anything else as an opaque free predicate. Raises
    Undetermined on a shape it cannot bracket, rather than guessing.
    """
    s = text.strip()
    if s.startswith("${") and s.endswith("}"):
        s = s[2:-1]
    s = s.strip()
    if not s:
        raise Undetermined("empty test expression")

    tokens = _tokenize(s)
    parser = _Parser(tokens)
    expr = parser.parse_or()
    if parser.peek() is not None:
        raise Undetermined("trailing input in test expression: %r" % s)
    return expr


def _tokenize(s: str) -> list:
    """Split an EL expression into (, ), &&, ||, !, and opaque operand chunks.

    Operand chunks stop at a top-level operator; quoted strings and nested parentheses are
    consumed whole so that `hasRole('a')` and `map['x.y'] eq 'z'` survive intact.
    """
    tokens = []
    i = 0
    n = len(s)
    operand = []

    def flush():
        chunk = "".join(operand).strip()
        operand.clear()
        if chunk:
            tokens.append(("operand", chunk))

    while i < n:
        c = s[i]
        if c in "'\"":
            quote = c
            j = i + 1
            while j < n and s[j] != quote:
                j += 1
            if j >= n:
                raise Undetermined("unterminated string literal")
            operand.append(s[i:j + 1])
            i = j + 1
            continue
        if c == "(":
            # A parenthesis directly after an identifier is a call -- part of the operand.
            # Otherwise it groups a sub-expression.
            prefix = "".join(operand).rstrip()
            if prefix and (prefix[-1].isalnum() or prefix[-1] in "_$."):
                depth = 0
                j = i
                while j < n:
                    if s[j] in "'\"":
                        quote = s[j]
                        j += 1
                        while j < n and s[j] != quote:
                            j += 1
                    elif s[j] == "(":
                        depth += 1
                    elif s[j] == ")":
                        depth -= 1
                        if depth == 0:
                            break
                    j += 1
                if j >= n:
                    raise Undetermined("unbalanced parentheses in call")
                operand.append(s[i:j + 1])
                i = j + 1
                continue
            flush()
            tokens.append(("lparen", "("))
            i += 1
            continue
        if c == ")":
            flush()
            tokens.append(("rparen", ")"))
            i += 1
            continue
        if c == "[":
            # Map/array subscript -- part of the operand, brackets and quotes included.
            depth = 0
            j = i
            while j < n:
                if s[j] in "'\"":
                    quote = s[j]
                    j += 1
                    while j < n and s[j] != quote:
                        j += 1
                elif s[j] == "[":
                    depth += 1
                elif s[j] == "]":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            if j >= n:
                raise Undetermined("unbalanced subscript")
            operand.append(s[i:j + 1])
            i = j + 1
            continue
        if s.startswith("&&", i) or s.startswith(" and ", i):
            flush()
            tokens.append(("and", "&&"))
            i += 2 if s.startswith("&&", i) else 5
            continue
        if s.startswith("||", i) or s.startswith(" or ", i):
            flush()
            tokens.append(("or", "||"))
            i += 2 if s.startswith("||", i) else 4
            continue
        if c == "!" and not s.startswith("!=", i):
            # `!` negates only when it opens an operand; `a != b` is handled above and
            # `!empty x` is an operand of its own.
            if "".join(operand).strip():
                operand.append(c)
                i += 1
                continue
            if s.startswith("!empty", i):
                operand.append(c)
                i += 1
                continue
            tokens.append(("not", "!"))
            i += 1
            continue
        operand.append(c)
        i += 1

    flush()
    return tokens


class _Parser:
    def __init__(self, tokens):
        self.tokens = tokens
        self.pos = 0

    def peek(self):
        return self.tokens[self.pos] if self.pos < len(self.tokens) else None

    def take(self):
        tok = self.peek()
        if tok is None:
            raise Undetermined("unexpected end of test expression")
        self.pos += 1
        return tok

    def parse_or(self) -> Expr:
        parts = [self.parse_and()]
        while self.peek() and self.peek()[0] == "or":
            self.take()
            parts.append(self.parse_and())
        return parts[0] if len(parts) == 1 else Expr("or", *parts)

    def parse_and(self) -> Expr:
        parts = [self.parse_unary()]
        while self.peek() and self.peek()[0] == "and":
            self.take()
            parts.append(self.parse_unary())
        return parts[0] if len(parts) == 1 else Expr("and", *parts)

    def parse_unary(self) -> Expr:
        tok = self.peek()
        if tok is None:
            raise Undetermined("unexpected end of test expression")
        if tok[0] == "not":
            self.take()
            return Expr("not", self.parse_unary())
        if tok[0] == "lparen":
            self.take()
            inner = self.parse_or()
            closing = self.take()
            if closing[0] != "rparen":
                raise Undetermined("expected ) in test expression")
            return inner
        if tok[0] == "operand":
            self.take()
            return _operand_expr(tok[1])
        raise Undetermined("unexpected token %r in test expression" % (tok[1],))


def _operand_expr(chunk: str) -> Expr:
    """One operand chunk to an Expr: a principal predicate, or an opaque free one."""
    negated = False
    body = chunk.strip()
    while body.startswith("!") and not body.startswith("!empty"):
        negated = not negated
        body = body[1:].strip()

    roles = HAS_ROLE.findall(body)
    perms = HAS_PERMISSION.findall(body)
    if roles or perms:
        # A principal call must be the whole operand. `hasRole('x') eq true` or any other
        # decoration around it is a shape this parser has not been taught to read.
        stripped = HAS_ROLE.sub("", HAS_PERMISSION.sub("", body)).strip()
        if stripped or len(roles) + len(perms) != 1:
            raise Undetermined("unrecognised predicate around a principal call: %r" % chunk)
        node = Expr("role", roles[0]) if roles else Expr("perm", perms[0])
        return Expr("not", node) if negated else node

    if "userSession" in body:
        # Some other userSession call gates this row. Guessing whether it is narrower or
        # wider than the page would be exactly the silent skip this gate exists to avoid.
        raise Undetermined("unmodelled userSession predicate: %r" % chunk)

    node = Expr("free", True)
    return Expr("not", node) if negated else node


# ----------------------------------------------------------------------------- main.jsp

class Row:
    def __init__(self, line_no, href, tests, undetermined=None):
        self.line_no = line_no
        self.href = href
        self.tests = tests            # list of (line_no, test-text) outermost first
        self.undetermined = undetermined

    def expr(self) -> Expr:
        parts = [parse_el(t) for _, t in self.tests]
        if not parts:
            return Expr("free", True)
        return parts[0] if len(parts) == 1 else Expr("and", *parts)


def parse_menu(path: str) -> tuple:
    """Return (rows, section_count). Rows carry their enclosing <c:if> stack."""
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")

    start = end = None
    for i, line in enumerate(lines):
        if start is None and MENU_START.search(line):
            start = i
        elif start is not None and MENU_END.search(line):
            end = i
            break
    if start is None or end is None:
        raise Undetermined(
            "could not find the admin off-canvas menu region in %s "
            "(looked for id=\"offCanvas\" ... </nav>)" % path)

    rows = []
    stack = []          # (line_no, test) for each open block-level <c:if>
    sections = 0
    in_comment = False

    for idx in range(start, end + 1):
        raw = lines[idx]
        line_no = idx + 1
        text = raw.strip()

        # JSP comments can wrap rows out of the menu entirely; a commented-out row is not
        # shown to anyone, so it is neither a finding nor a checked row.
        if in_comment:
            if "--%>" in text:
                in_comment = False
            continue
        if text.startswith("<%--"):
            if "--%>" not in text:
                in_comment = True
            continue

        if 'class="section-title"' in text:
            sections += 1

        block = BLOCK_IF.match(text)
        if block:
            stack.append((line_no, block.group(1)))
            continue
        if text == "</c:if>":
            if not stack:
                raise Undetermined("unbalanced </c:if> at %s:%d" % (path, line_no))
            stack.pop()
            continue

        opens = len(CIF_OPEN.findall(text))
        closes = len(CIF_CLOSE.findall(text))
        if opens != closes:
            # Not a lone opener and not balanced: a shape this parser cannot track.
            raise Undetermined(
                "unbalanced <c:if> nesting at %s:%d -- this parser expects a block-opening "
                "<c:if> alone on its line and every other <c:if> balanced on its own line: %s"
                % (path, line_no, text[:120]))

        if not text.startswith("<li"):
            continue

        href_match = HREF.search(text)
        if href_match is None:
            # A row with no ${ctx} link: the section-title <li>, or a row whose target is
            # computed. The former is not a link; the latter cannot be resolved.
            if 'class="section-title"' in text or "<a " not in text:
                continue
            rows.append(Row(line_no, None, list(stack),
                            undetermined="row links somewhere this parser cannot read"))
            continue

        rows.append(Row(line_no, href_match.group(1), list(stack)))

    if stack:
        raise Undetermined("%d unclosed <c:if> block(s) in the menu region of %s "
                           "(first at line %d)" % (len(stack), path, stack[0][0]))
    return rows, sections


# --------------------------------------------------------------------------- layout XML

class PageDecl:
    def __init__(self, name, roles, capabilities, source):
        self.name = name
        self.roles = roles
        self.capabilities = capabilities
        self.source = source

    def admits(self, held: frozenset) -> bool:
        """Mirror WebComponentCommand.allowsUser: role OR capability, open when neither."""
        if not self.roles and not self.capabilities:
            return True
        for role in self.roles:
            if role == "guest" and ("role", "guest") in held:
                return True
            if role == "users" and ("role", "users") in held:
                return True
            if ("role", role) in held:
                return True
        for capability in self.capabilities:
            if ("perm", capability) in held:
                return True
        return False

    def describe(self) -> str:
        bits = []
        if self.roles:
            bits.append('role="%s"' % ",".join(self.roles))
        if self.capabilities:
            bits.append('capability="%s"' % ",".join(self.capabilities))
        return " ".join(bits) if bits else "(no role or capability -- open to everyone)"


def load_pages(root: str) -> dict:
    pages = {}
    for path in sorted(glob.glob(os.path.join(root, LAYOUT_GLOB))):
        rel = os.path.relpath(path, root)
        tree = ET.parse(path)
        for element in tree.getroot().iter("page"):
            name = element.get("name")
            if not name:
                continue
            roles = [r.strip() for r in element.get("role", "").split(",") if r.strip()]
            caps = [c.strip() for c in element.get("capability", "").split(",") if c.strip()]
            # First declaration wins, matching XMLPageLoader's map-building order.
            pages.setdefault(name, PageDecl(name, roles, caps, rel))
    return pages


def locate_page(pages: dict, path: str):
    """Mirror WebPageXmlLayoutCommand.locatePage(): exact, then shorter prefixes."""
    if path in pages:
        return pages[path]
    segments = path.split("/")
    for cut in range(len(segments) - 1, 1, -1):
        candidate = "/".join(segments[:cut])
        if candidate in pages:
            return pages[candidate]
    return None


# ----------------------------------------------------------------------------- checking

def principal_universe(rows, pages) -> list:
    """Every role/capability the menu or the layout XML names, plus the pseudo-roles."""
    roles = set(PSEUDO_ROLES)
    perms = set()
    for row in rows:
        for _, test in row.tests:
            roles.update(HAS_ROLE.findall(test))
            perms.update(HAS_PERMISSION.findall(test))
    for page in pages.values():
        roles.update(page.roles)
        perms.update(page.capabilities)
    return sorted(("role", r) for r in roles) + sorted(("perm", p) for p in perms)


def describe_principal(atom) -> str:
    return ("role %s" if atom[0] == "role" else "capability %s") % atom[1]


def token(atom) -> str:
    """Canonical allowlist spelling of one principal: role:x / capability:x."""
    return ("role:%s" if atom[0] == "role" else "capability:%s") % atom[1]


def check(root: str):
    jsp_path = os.path.join(root, MAIN_JSP)
    if not os.path.isfile(jsp_path):
        sys.exit("error: %s not found (run from the repository root)" % jsp_path)
    layout_dir = os.path.dirname(os.path.join(root, LAYOUT_GLOB))
    if not os.path.isdir(layout_dir):
        sys.exit("error: %s not found (run from the repository root)" % layout_dir)

    pages = load_pages(root)
    try:
        rows, sections = parse_menu(jsp_path)
    except Undetermined as exc:
        return None, None, None, str(exc)

    universe = principal_universe(rows, pages)
    # Every combination of held roles/capabilities, so negation and mixed &&/|| nesting are
    # evaluated rather than approximated. The universe is small (single digits), and the
    # cost is bounded and checked below.
    if len(universe) > 20:
        return None, None, None, (
            "principal universe of %d is too large to enumerate exhaustively; this gate "
            "needs rewriting before it can be trusted" % len(universe))
    # Only principal sets that can actually render this menu are considered. main.jsp draws
    # the admin menu on /admin* pages, so the viewer of any row has already opened some
    # admin page. A set that opens none of them -- the empty set, or a role that exists
    # only on the public site -- never sees the menu, and counting it would flag every row
    # of the un-gated Admin section against a viewer who cannot reach it.
    admin_pages = [p for name, p in pages.items() if name.startswith("/admin")]
    subsets = [frozenset(c)
               for size in range(1, len(universe) + 1)
               for c in itertools.combinations(universe, size)]
    subsets = [s for s in subsets if any(p.admits(s) for p in admin_pages)]
    if not subsets:
        return None, None, None, (
            "no principal can open any /admin page, so no menu row is reachable; "
            "this gate cannot check anything and is not passing")

    findings = []
    undetermined = []
    baselined = []
    checked = 0

    for row in rows:
        if row.undetermined:
            undetermined.append((row.line_no, row.undetermined))
            continue
        page = locate_page(pages, row.href)
        if page is None:
            undetermined.append((row.line_no,
                                 "href %s resolves to no <page> declaration in the layout XML"
                                 % row.href))
            continue
        try:
            expr = row.expr()
        except Undetermined as exc:
            undetermined.append((row.line_no, str(exc)))
            continue

        checked += 1
        leaks = []
        for held in subsets:
            if visible_to(expr, held) and not page.admits(held):
                leaks.append(held)
        if not leaks:
            continue
        # Reduce to the MINIMAL leaking sets -- those with no leaking proper subset. Once
        # `data-manager` alone sees the dead link, every superset containing it leaks too;
        # reporting those would name `guest` and `users` as if they were causes when they
        # are only passengers. The minimal sets are the actual principals at fault.
        minimal = [h for h in leaks
                   if not any(other < h for other in leaks)]
        named = sorted(" + ".join(describe_principal(a) for a in sorted(h)) for h in minimal)
        # Ratchet on the principals in those minimal sets, so a row that starts leaking to
        # someone new fails even when it already leaked to someone else.
        involved = {token(a) for h in minimal for a in h}
        allowed = ALLOWLIST.get(row.href)
        if allowed is not None and involved <= allowed:
            baselined.append((row.line_no, row.href, named))
            continue
        findings.append((row.line_no, row.href, page, named, row.tests,
                         sorted(involved - (allowed or set()))))

    # Allowlist entries whose row no longer leaks, or is gone. Not a failure; a nudge to
    # trim the baseline, matching how check-inline-handlers.py reports stale debt.
    still_leaking = {href for _, href, _ in baselined} | {f[1] for f in findings}
    stale = sorted(set(ALLOWLIST) - still_leaking)

    return rows, sections, (findings, undetermined, checked, baselined, stale), None


# ------------------------------------------------------------------------------- report

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--strict", action="store_true",
                    default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    rows, sections, result, fatal = check(args.root)

    lines = ["Admin nav / page permission check", ""]

    if fatal:
        lines.append("  COULD NOT DETERMINE  %s" % fatal)
        lines.append("")
        lines.append("Summary: the menu could not be parsed; 0 rows checked.")
        print("\n".join(lines))
        _write_step_summary(None, None, 0, fatal)
        if args.strict:
            print()
            print("FAIL: the admin menu could not be parsed, so no row was checked.")
            print("This gate refuses to report a clean run it did not actually perform.")
            return 1
        return 0

    findings, undetermined, checked, baselined, stale = result

    for line_no, href, page, named, tests, new_principals in findings:
        lines.append("  DEAD LINK  %s:%d  %s" % (MAIN_JSP, line_no, href))
        lines.append("             row is shown to: %s" % ", ".join(named))
        lines.append("             page declares:   %s  (%s)" % (page.describe(), page.source))
        gate = " && ".join(t.strip() for _, t in tests) \
            or "(no <c:if> -- shown to everyone who can render the admin menu)"
        lines.append("             visibility test: %s" % gate)
        if href in ALLOWLIST:
            lines.append("             newly leaking to: %s" % ", ".join(new_principals))
    if findings:
        lines.append("")

    for line_no, why in undetermined:
        lines.append("  UNDETERMINED  %s:%d  %s" % (MAIN_JSP, line_no, why))
    if undetermined:
        lines.append("")

    for line_no, href, named in baselined:
        lines.append("  known debt  %s:%d  %s  (shown to %s)"
                     % (MAIN_JSP, line_no, href, ", ".join(named)))
    for href in stale:
        lines.append("  stale allowlist entry  %s no longer leaks -- remove it from "
                     "ALLOWLIST" % href)
    if baselined or stale:
        lines.append("")

    lines.append("Summary: %d menu row(s) checked across %d section(s); "
                 "%d dead link(s), %d undetermined, %d allowlisted."
                 % (checked, sections, len(findings), len(undetermined), len(baselined)))
    if checked == 0:
        lines.append("Summary: 0 rows checked -- the menu parser matched nothing, "
                     "which is a failure, not a pass.")

    print("\n".join(lines))
    _write_step_summary(findings, undetermined, checked, None, baselined)

    failed = bool(findings) or bool(undetermined) or checked == 0
    if args.strict and failed:
        print()
        if checked == 0:
            print("FAIL: no menu row was checked. Either the menu moved or the parser broke;")
            print("either way this is not a clean run.")
        if findings:
            print("FAIL: a menu row is shown to someone who cannot open the page it links to.")
            print("They see the link and are denied when they click it. Either narrow the row")
            print("with its own <c:if>, or widen the page's role=/capability= declaration --")
            print("widening it is an authorization decision, so make it deliberately.")
        if undetermined:
            print("FAIL: a menu row could not be evaluated. This gate reports what it could not")
            print("read rather than skipping it; teach the parser the new shape, or restructure")
            print("the row to the conventional one (a block <c:if> alone on its line).")
        return 1
    return 0


def _write_step_summary(findings, undetermined, checked, fatal, baselined=()) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    with open(summary_path, "a") as fh:
        fh.write("## Admin nav / page permissions\n\n")
        if fatal:
            fh.write("**Could not parse the admin menu:** %s\n" % fatal)
            return
        if findings:
            fh.write("**%d menu row(s) link to a page the viewer cannot open.**\n\n" % len(findings))
            fh.write("| Row | Link | Shown to | Page declares |\n|---|---|---|---|\n")
            for line_no, href, page, named, _, _new in findings:
                fh.write("| `main.jsp:%d` | `%s` | %s | `%s` |\n"
                         % (line_no, href, ", ".join(named), page.describe()))
            fh.write("\n")
        if undetermined:
            fh.write("**%d row(s) could not be evaluated.**\n\n" % len(undetermined))
            for line_no, why in undetermined:
                fh.write("- `main.jsp:%d` -- %s\n" % (line_no, why))
            fh.write("\n")
        if not findings and not undetermined:
            fh.write("No new dead links. %d row(s) checked, %d allowlisted as known debt.\n"
                     % (checked, len(baselined)))


if __name__ == "__main__":
    sys.exit(main())
