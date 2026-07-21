## With Tomcat
## CATALINA_HOME:   /usr/local/tomcat
# ant package
# docker build -f docker/app/Dockerfile -t simis-cms:latest .
# docker image ls
# docker run -p 80:8080 --env-file=.env simis-cms:latest

FROM tomcat:9.0-jdk21

# DISA Apache Tomcat 9 STIG hardening. Overlay the hardened server.xml (shutdown port
# disabled, HTTP TRACE off, server banner suppressed, no error-page disclosure, autoDeploy
# off, access logging), add JVM hardening via setenv.sh (RECYCLE_FACADES), and remove the
# bundled default web applications (manager, host-manager, docs, examples) that the STIG
# requires be absent. Coverage & rule mapping: docker-tomcat9-stig-hardening.md (private).
COPY ./docker/app/conf/server.xml /usr/local/tomcat/conf/server.xml
COPY ./docker/app/conf/setenv.sh /usr/local/tomcat/bin/setenv.sh
RUN chmod 0750 /usr/local/tomcat/bin/setenv.sh && rm -rf /usr/local/tomcat/webapps.dist

COPY ./target/simis-cms.war /usr/local/tomcat/webapps/ROOT.war
ENV CATALINA_OPTS="-XX:InitialRAMPercentage=10 -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 CMD curl -I -f --max-time 5 --header "X-Monitor: healthcheck" http://localhost:8080 || exit 1
CMD ["catalina.sh", "run"]
