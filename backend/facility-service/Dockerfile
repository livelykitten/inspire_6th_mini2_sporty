# syntax=docker/dockerfile:1
# 담당: 이서현 (@seohynee) · 리뷰: 반정욱 (@salieri009)
# 서비스: facility-service · 포트 8003 · 의존: facility-db(3306), kafka, discovery, PUBLIC_DATA_API_KEY
# 작업 위치: backend/facility-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/facility-service/Dockerfile -t sporty/facility-service:local backend

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :facility-service:bootJar -x test --no-daemon \
    && cp facility-service/build/libs/facility-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8003
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
