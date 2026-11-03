# syntax=docker/dockerfile:1
# 담당: 김우석 (@woo1956) · 리뷰: 반정욱 (@salieri009)
# 서비스: config-service · 포트 8888 · 의존: discovery(Eureka 등록), config-repo/ (이미지에 포함), ENCRYPT_KEY
# 작업 위치: backend/config-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/config-service/Dockerfile --build-context config-repo=config-repo -t sporty/config-service:local backend
# config-repo/ 는 빌드 컨텍스트(backend/) 밖이라 named context 로 받는다 (아래 빌드 명령의 --build-context).

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :config-service:bootJar -x test --no-daemon \
    && cp config-service/build/libs/config-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENV CONFIG_REPO_PATH=file:/config-repo
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
COPY --from=config-repo --chown=sporty:sporty . /config-repo
USER sporty

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
