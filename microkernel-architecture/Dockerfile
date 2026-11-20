FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q package -DskipTests \
 && mkdir /plugins && cp plugins/*/target/plugin-*-SNAPSHOT.jar /plugins/

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/shipping-kernel/target/shipping-kernel-*.jar app.jar
# Seed plug-ins; the compose file mounts a host folder over /plugins so jars can be dropped/removed live.
COPY --from=build /plugins /seed-plugins
ENV PLUGINS_DIR=/plugins
ENTRYPOINT ["sh", "-c", "[ -n \"$(ls -A /plugins 2>/dev/null)\" ] || cp /seed-plugins/*.jar /plugins/; exec java -jar app.jar"]
