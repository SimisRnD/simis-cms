## With Tomcat
## CATALINA_HOME:   /usr/local/tomcat
# ant package
# docker build -f docker/app/Dockerfile -t simis-cms:latest .
# docker image ls
# docker run -p 80:8080 --env-file=.env simis-cms:latest

FROM tomcat:9.0-jdk21

# DISA Apache Tomcat 9 STIG hardening. Overlay the hardened server.xml (shutdown port
# disabled, HTTP TRACE off, server banner suppressed, no error-page disclosure, autoDeploy
# off, access logging), add JVM hardening via setenv.sh (RECYCLE_FACADES -- it APPENDS to
# CATALINA_OPTS below, so it composes with the Application Insights agent flags), and remove
# the bundled default web applications (manager, host-manager, docs, examples) that the STIG
# requires be absent. Coverage & rule mapping: docker-tomcat9-stig-hardening.md (private).
COPY ./docker/app/conf/server.xml /usr/local/tomcat/conf/server.xml
COPY ./docker/app/conf/setenv.sh /usr/local/tomcat/bin/setenv.sh
RUN chmod 0750 /usr/local/tomcat/bin/setenv.sh && rm -rf /usr/local/tomcat/webapps.dist

# Application Insights Java agent (APM / operational telemetry). Baked into the image because App Service
# for Containers cannot auto-instrument a custom container. The agent auto-instruments Tomcat/JDBC/JVM/HTTP
# and is INERT until APPLICATIONINSIGHTS_CONNECTION_STRING is supplied at deploy (from Key Vault). With no
# connection string it logs a single startup notice ("No connection string provided") and self-disables --
# the JVM and app are unaffected, so local/CI runs (which have no connection string) still boot normally.
# Pinned + SHA-256 verified for supply-chain integrity; it auto-loads applicationinsights.json from /opt.
ARG APPLICATIONINSIGHTS_VERSION=3.7.9
ARG APPLICATIONINSIGHTS_SHA256=4ab7a442bf9defc7475d026c6b49042793ebb010c391a1db91ae07da0c04d848
RUN curl -fsSL -o /opt/applicationinsights-agent.jar \
      "https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${APPLICATIONINSIGHTS_VERSION}/applicationinsights-agent-${APPLICATIONINSIGHTS_VERSION}.jar" \
    && echo "${APPLICATIONINSIGHTS_SHA256}  /opt/applicationinsights-agent.jar" | sha256sum -c -
COPY ./docker/app/applicationinsights.json /opt/applicationinsights.json

COPY ./target/simis-cms.war /usr/local/tomcat/webapps/ROOT.war
ENV CATALINA_OPTS="-javaagent:/opt/applicationinsights-agent.jar -XX:InitialRAMPercentage=10 -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 CMD curl -f --max-time 5 http://localhost:8080/healthz || exit 1
CMD ["catalina.sh", "run"]
