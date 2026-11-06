# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests already run in the CI "build" job
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user (numeric UID so Kubernetes runAsNonRoot can verify it)
RUN groupadd -g 10001 app && useradd -r -u 10001 -g app app
COPY --from=build --chown=app:app /workspace/target/cicd-demo-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
