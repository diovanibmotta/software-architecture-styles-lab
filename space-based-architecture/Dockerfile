# Multi-stage build shared by both runnable modules: docker build --build-arg MODULE=ticketing-processing-unit .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY ticketing-contracts/pom.xml ticketing-contracts/
COPY ticketing-processing-unit/pom.xml ticketing-processing-unit/
COPY ticketing-data-writer/pom.xml ticketing-data-writer/
COPY . .
ARG MODULE
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar app.jar
# Flags required by Hazelcast on JDK 17+
ENV JAVA_OPTS="--add-modules java.se \
 --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
 --add-opens java.base/java.lang=ALL-UNNAMED \
 --add-opens java.base/java.nio=ALL-UNNAMED \
 --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
 --add-opens java.management/sun.management=ALL-UNNAMED \
 --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
