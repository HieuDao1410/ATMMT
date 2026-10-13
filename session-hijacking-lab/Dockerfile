FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/

# Build and run the HTTP integration tests with the same Java major version.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B verify

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/target/session-hijacking-lab-0.0.1-SNAPSHOT.jar app.jar

# Listen on the container network; Compose publishes only to the host loopback.
ENV SERVER_ADDRESS=0.0.0.0
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
