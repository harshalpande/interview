FROM maven:3.9.3-eclipse-temurin-17 as builder
WORKDIR /build
COPY sandbox/pom.xml ./pom.xml
COPY sandbox/src ./src
RUN mvn clean package -DskipTests

FROM python:3.12-slim-bookworm
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

COPY backend/libs/*.jar /app/libs/
COPY --from=builder /build/target/java-compiler-sandbox-0.0.1-SNAPSHOT.jar app.jar

ENV SANDBOX_LIBS=/app/libs
ENV PYTHON_EXECUTABLE=python3

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
