FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
COPY libs/ libs/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/target/java-compiler-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

