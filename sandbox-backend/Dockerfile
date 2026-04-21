FROM eclipse-temurin:17-jdk-jammy as java-runtime

FROM python:3.12-slim-bookworm
WORKDIR /app
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY backend/libs/*.jar /app/libs/
COPY sandbox-backend/target/sandbox-backend-0.0.1-SNAPSHOT.jar app.jar

ENV SANDBOX_LIBS=/app/libs
ENV PYTHON_EXECUTABLE=python3

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
