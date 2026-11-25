# Builds one service of the repo: docker build --build-arg MODULE=order-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -q package -DskipTests -pl ${MODULE} -am

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*-exec.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
