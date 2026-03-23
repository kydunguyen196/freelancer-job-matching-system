FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle :services:contract-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
RUN useradd --system --create-home --uid 1001 appuser
COPY --from=builder /workspace/services/contract-service/build/libs/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
