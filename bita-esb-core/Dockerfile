# ESB Core — one container per service (SERVICE_ID), configured from environment variables
# (see EsbConfig). Build after `mvn package -pl bita-esb-core -am`, which produces the shaded jar.
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/bita-esb-core-*-SNAPSHOT.jar /app/esb.jar

ENV HTTP_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/esb.jar"]
