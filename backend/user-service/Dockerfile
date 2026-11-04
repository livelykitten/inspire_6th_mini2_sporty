# syntax=docker/dockerfile:1
# 담당: 송석주 (@songsj27) · 리뷰: 반정욱 (@salieri009)
# 서비스: user-service · 포트 8001 · 의존: user-db(3306), redis, kafka, discovery, JWT_SECRET_KEY
# 작업 위치: backend/user-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/user-service/Dockerfile -t sporty/user-service:local backend

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :user-service:bootJar -x test --no-daemon \
    && cp user-service/build/libs/user-service-*-SNAPSHOT.jar /workspace/app.jar

# 런타임은 Alpine JRE(257MB, Ubuntu JRE 는 432MB)로 이미지를 줄인다. Alpine 에는 curl · bash 가 없다 (헬스체크는 busybox wget 사용).
FROM eclipse-temurin:17-jre-alpine AS runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN addgroup -S -g 1001 sporty && adduser -S -u 1001 -G sporty sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
