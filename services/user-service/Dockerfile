FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :services:user-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/services/user-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
