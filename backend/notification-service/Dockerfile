# syntax=docker/dockerfile:1
# 담당: 민경완 · 리뷰: 반정욱 (@salieri009)
# 서비스: notification-service · 포트 8010 · 의존: discovery, config, Kafka, Gmail SMTP
# 작업 위치: backend/notification-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/notification-service/Dockerfile -t sporty/notification-service:local backend

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Gradle 멀티 프로젝트의 notification-service 모듈을 빌드한다.
COPY . .

# Windows 체크아웃 시 gradlew의 CRLF 줄바꿈을 변환한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 병렬 이미지 빌드 시 Gradle 캐시 잠금 충돌 방지.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :notification-service:bootJar -x test --no-daemon \
    && cp notification-service/build/libs/notification-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine AS runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN addgroup -S -g 1001 sporty && adduser -S -u 1001 -G sporty sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app/app.jar"]