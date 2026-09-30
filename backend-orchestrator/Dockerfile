# Stage 1: Build Java 21 Spring Boot Artifact
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

# Stage 2: Minimal Distroless / Hardened Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
