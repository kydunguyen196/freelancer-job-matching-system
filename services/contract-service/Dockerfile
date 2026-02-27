FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :services:contract-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/services/contract-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
