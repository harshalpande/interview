FROM maven:3.9.3-eclipse-temurin-17 as builder
WORKDIR /build
COPY sandbox/pom.xml ./pom.xml
COPY sandbox/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY backend/libs/*.jar /app/libs/
COPY --from=builder /build/target/java-compiler-sandbox-0.0.1-SNAPSHOT.jar app.jar

ENV SANDBOX_LIBS=/app/libs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
