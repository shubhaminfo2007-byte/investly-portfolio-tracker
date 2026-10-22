# ---- Build stage: compile and package the Spring Boot jar (Java 17, Maven) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Run stage: small Java 17 runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Defaults. Render overrides PORT; set PORTFOLIO_DATA_DIR to your persistent disk mount path.
ENV PORT=8080 \
    THYMELEAF_CACHE=true \
    PORTFOLIO_DATA_DIR=/app/data
EXPOSE 8080
# MaxRAMPercentage keeps the JVM inside small (512 MB) instances.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
