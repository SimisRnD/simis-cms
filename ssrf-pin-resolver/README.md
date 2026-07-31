# SSRF connect-time DNS pin resolver

Closes the DNS-rebinding gap tracked in issue #760: `RemoteUrlValidationCommand` validates a
URL's host once, but the JDK `HttpClient` re-resolves that same hostname itself when it
connects, so a hostname that resolved to a public address at validation time could resolve to
an internal one moments later. This module lets `HttpGetCommand.executeUserUrl()` pin the
*exact* address(es) `RemoteUrlValidationCommand` already validated, so the connect-time lookup
returns those bytes instead of asking DNS again -- there is no second lookup left to race.

## Why this is not under `src/main/java`

This is a `java.net.spi.InetAddressResolverProvider`. The JDK's `InetAddress` machinery
discovers providers via `ServiceLoader` exactly once, on whichever classloader performs the
*first* DNS lookup of the JVM's life, and then holds that decision forever. In a real Tomcat
11 container that first lookup happens during Tomcat's own bootstrap (before any webapp
classloader exists), on Tomcat's shared/Common classloader -- so a provider bundled into the
WAR (`WEB-INF/lib` or `WEB-INF/classes`) is discovered too late and is never consulted. This
was verified empirically, not assumed: the identical class shipped webapp-scoped measurably
does nothing; shipped on `CATALINA_HOME/lib` it genuinely intercepts `HttpClient`'s
connect-time resolution.

Practically, that means these two classes must be:

- compiled against by application code (`HttpGetCommand` imports `ConnectAddressPin`), but
- **excluded** from the WAR, and
- present on Tomcat's own `CATALINA_HOME/lib` at container boot.

That is exactly the "provided scope" treatment `jakarta.servlet-api` already gets in this
project (see `lib/compile/jee` -- compiled against, never bundled, supplied by the container).
This module is the same pattern for code we wrote ourselves instead of a third-party API jar.

## Build

`ant pin-resolver-jar` (a dependency of `compile`, so it also runs as part of a normal
`ant compile` / `ant package`) compiles this directory with plain `javac` -- no dependency on
the rest of the app's classpath, since `java.net.spi.*` is JDK-builtin -- and jars the result,
together with the `META-INF/services` registration, to
`target/simis-cms-ssrf-pin-resolver.jar`. That jar is never committed to git (it is a build
artifact, like everything else under `target/`) and is added to Ant's compile classpath
compile-only; `package`/`webapp` do not include it, since they assemble `WEB-INF/lib` only
from `lib/build`.

`docker/app/Dockerfile` copies the built jar to `/usr/local/tomcat/lib/`.

## Deployments other than this project's Docker image

This project's README documents a second, Docker-independent deployment path: build the `.war`
(`ant package`) and deploy it to any Java 21 servlet container. That path does **not**
automatically get this jar -- there is nothing in the WAR that would put it on such a
container's shared classpath, by design (see above).

`HttpGetCommand` handles this: it probes once, at class-init, whether `ConnectAddressPin`
actually links, and falls back to the pre-issue-#760 behavior (SSRF guard still enforced, just
not pinned -- no worse than before this change, just not improved) if it does not, logging a
warning that names the missing jar and this file. It does **not** throw or break the fetch.
Confirmed by building `target/simis-cms.jar` (or the exploded `WEB-INF/classes` +
`WEB-INF/lib`) without `target/simis-cms-ssrf-pin-resolver.jar` on the classpath and calling
`HttpGetCommand.executeUserUrl(...)`: before this fallback existed, that threw
`NoClassDefFoundError` out of `executeUserUrl` on first use; with it, `executeUserUrl` logs the
warning once and otherwise behaves exactly as it did before issue #760.

An operator deploying this application's `.war` to their own Tomcat 11 (or other Java 21
servlet container) who wants the DNS-rebinding fix should build this module
(`ant pin-resolver-jar`) and copy `target/simis-cms-ssrf-pin-resolver.jar` to their own
container's shared classpath (`CATALINA_HOME/lib` for Tomcat) as part of their upgrade steps,
the same way this project's Docker image does. This is a real, currently-undocumented gap in
the upgrade instructions for non-Docker deployments -- flagged here rather than silently left
for someone to discover as a missing security improvement with no explanation.

## Do not duplicate these classes into the WAR

Tomcat's webapp classloaders are child-first. If a copy of `ConnectAddressPin` or
`ConnectAddressResolverProvider` ever ends up in `WEB-INF/lib` or `WEB-INF/classes` as well as
`CATALINA_HOME/lib`, the webapp loads its own copy, and `HttpGetCommand` would write a pin
that the resolver (bound to the `CATALINA_HOME/lib` copy) never reads -- silently reopening the
DNS-rebinding gap this module exists to close. `build.xml`'s `package`/`webapp` targets assemble
`WEB-INF/lib` only from `lib/build`, so this module's jar is never written there by the current
build script -- but a future, unrelated change to those filesets (broadening a `<fileset dir>`
include, or moving `pin.resolver.build.dir` back under `build.dir` as an early draft of this
change did) could silently reintroduce it.

`tools/check-war-completeness.py` gates this directly: its `FORBIDDEN` dict asserts
`com.simisinc.platform.provided.net` is positively absent from the exploded WAR class tree (not
just "not reported missing" -- the opposite check from the rest of that script), and fails
under `--strict`, which `war-completeness.yml` already runs on every push and PR to `main`. It
is still worth re-confirming by hand (`jar tf target/simis-cms.war | grep -i provided/net`)
right after hand-editing `build.xml` and before relying on CI to catch it, but a regression no
longer depends on someone remembering to do that.
