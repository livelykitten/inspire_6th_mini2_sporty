# syntax=docker/dockerfile:1
# W-23 서비스 7개 공용 Dockerfile. 빌드 컨텍스트는 backend/ 이고, 서비스는 build arg 로 고른다.
#
#   docker build -f backend/Dockerfile --build-arg SERVICE=gateway-service --build-arg PORT=8000 \
#                -t sporty/gateway-service:local backend
#
# 서비스 / 포트: gateway-service 8000 · discovery-service 8761 · config-service 8888 ·
#                user-service 8001 · match-service 8002 · facility-service 8003 · ai-service 8004

FROM eclipse-temurin:17-jdk AS build
ARG SERVICE
WORKDIR /workspace

# 서비스 소스. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${SERVICE}:bootJar -x test --no-daemon \
    && cp ${SERVICE}/build/libs/${SERVICE}-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre
ARG PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
