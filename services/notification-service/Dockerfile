FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :services:notification-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/services/notification-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
