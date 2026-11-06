# Build Java bytecode on the runner's native architecture.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew check bootJar --no-daemon

# Jammy supports the EC2 Graviton ARM64 target.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
