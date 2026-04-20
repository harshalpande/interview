# Multi-stage build for Java backend
FROM maven:3.9.3-eclipse-temurin-17 as builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY --from=builder /build/target/java-compiler-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

