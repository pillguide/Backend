# 빌드 단계
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

# 런타임 단계
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "-Duser.timezone=${TZ}", "app.jar" ]