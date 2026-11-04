# syntax=docker/dockerfile:1
# 담당: 김예림 (@kimrim818-afk) · 리뷰: 반정욱 (@salieri009)
# 서비스: match-service · 포트 8002 · 의존: match-db(3306), kafka, discovery
# 작업 위치: backend/match-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/match-service/Dockerfile -t sporty/match-service:local backend

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :match-service:bootJar -x test --no-daemon \
    && cp match-service/build/libs/match-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
