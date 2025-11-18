# --- Build Stage ---
# JDK 21 사용
FROM eclipse-temurin:21-jdk-jammy AS build

# 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# Gradle Wrapper 복사
COPY gradlew ./
COPY ../../../../../git/pillguide-backend/gradle ./gradle
RUN chmod +x ./gradlew

# 의존성 캐싱
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사
COPY src ./src
RUN ./gradlew build --no-daemon -x test


# --- Runtime Stage ---
# 경량 Java 21 사용
FROM eclipse-temurin:21-jre-alpine AS runtime

# 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# 빌드 결과 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너 8080 포트 사용 (바인딩X)
EXPOSE 8080

# 컨테이너 실행
ENTRYPOINT ["java", "-jar", "app.jar"]