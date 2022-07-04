## With Tomcat
## CATALINA_HOME:   /usr/local/tomcat
# docker build -f Dockerfile -t simis-cms .
# docker image ls
# docker run -p 80:8080 simis-cms

FROM tomcat:9.0-jdk17
COPY ./target/simis-cms.war /usr/local/tomcat/webapps/ROOT.war
ENV CATALINA_OPTS="-XX:InitialRAMPercentage=10 -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
CMD ["catalina.sh", "run"]
