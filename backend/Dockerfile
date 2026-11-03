# syntax=docker/dockerfile:1
# ==============================================================================
# 생성물 - 직접 수정 금지. 원본은 backend/{서비스}/Dockerfile 이다.
# 담당: 반정욱 (@salieri009) · 재생성: bash scripts/gen-master-dockerfile.sh
# 사용: docker build --target {서비스} -t sporty/{서비스}:local backend
# ==============================================================================

# ----- [discovery-service] 담당: 김우석 (@woo1956) · 원본: backend/discovery-service/Dockerfile -----
# 담당: 김우석 (@woo1956) · 리뷰: 반정욱 (@salieri009)
# 서비스: discovery-service · 포트 8761 · 의존: 없음 (Eureka 서버, 단독 기동)
# 작업 위치: backend/discovery-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/discovery-service/Dockerfile -t sporty/discovery-service:local backend

FROM eclipse-temurin:17-jdk AS discovery-service-build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :discovery-service:bootJar -x test --no-daemon \
    && cp discovery-service/build/libs/discovery-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS discovery-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=discovery-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [config-service] 담당: 김우석 (@woo1956) · 원본: backend/config-service/Dockerfile -----
# 담당: 김우석 (@woo1956) · 리뷰: 반정욱 (@salieri009)
# 서비스: config-service · 포트 8888 · 의존: discovery(Eureka 등록), config-repo/ (이미지에 포함), ENCRYPT_KEY
# 작업 위치: backend/config-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/config-service/Dockerfile --build-context config-repo=config-repo -t sporty/config-service:local backend
# config-repo/ 는 빌드 컨텍스트(backend/) 밖이라 named context 로 받는다 (아래 빌드 명령의 --build-context).

FROM eclipse-temurin:17-jdk AS config-service-build
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

FROM eclipse-temurin:17-jre AS config-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
ENV CONFIG_REPO_PATH=file:/config-repo
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=config-service-build --chown=sporty:sporty /workspace/app.jar app.jar
COPY --from=config-repo --chown=sporty:sporty . /config-repo
USER sporty

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [gateway-service] 담당: 김우석 (@woo1956) · 원본: backend/gateway-service/Dockerfile -----
# 담당: 김우석 (@woo1956) · 리뷰: 반정욱 (@salieri009)
# 서비스: gateway-service · 포트 8000 · 의존: discovery, JWT_SECRET_KEY (user-service 와 같은 값), 업무 서비스 4개로 라우팅
# 작업 위치: backend/gateway-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/gateway-service/Dockerfile -t sporty/gateway-service:local backend

FROM eclipse-temurin:17-jdk AS gateway-service-build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :gateway-service:bootJar -x test --no-daemon \
    && cp gateway-service/build/libs/gateway-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS gateway-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=gateway-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [user-service] 담당: 송석주 (@songsj27) · 원본: backend/user-service/Dockerfile -----
# 담당: 송석주 (@songsj27) · 리뷰: 반정욱 (@salieri009)
# 서비스: user-service · 포트 8001 · 의존: user-db(3306), redis, kafka, discovery, JWT_SECRET_KEY
# 작업 위치: backend/user-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/user-service/Dockerfile -t sporty/user-service:local backend

FROM eclipse-temurin:17-jdk AS user-service-build
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

FROM eclipse-temurin:17-jre AS user-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=user-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [match-service] 담당: 김예림 (@kimrim818-afk) · 원본: backend/match-service/Dockerfile -----
# 담당: 김예림 (@kimrim818-afk) · 리뷰: 반정욱 (@salieri009)
# 서비스: match-service · 포트 8002 · 의존: match-db(3306), kafka, discovery
# 작업 위치: backend/match-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/match-service/Dockerfile -t sporty/match-service:local backend

FROM eclipse-temurin:17-jdk AS match-service-build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :match-service:bootJar -x test --no-daemon \
    && cp match-service/build/libs/match-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS match-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=match-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [facility-service] 담당: 이서현 (@seohynee) · 원본: backend/facility-service/Dockerfile -----
# 담당: 이서현 (@seohynee) · 리뷰: 반정욱 (@salieri009)
# 서비스: facility-service · 포트 8003 · 의존: facility-db(3306), kafka, discovery, PUBLIC_DATA_API_KEY
# 작업 위치: backend/facility-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/facility-service/Dockerfile -t sporty/facility-service:local backend

FROM eclipse-temurin:17-jdk AS facility-service-build
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

FROM eclipse-temurin:17-jre AS facility-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=facility-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8003
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ----- [ai-service] 담당: 서정운 (@Jeonun-real) · 원본: backend/ai-service/Dockerfile -----
# 담당: 서정운 (@Jeonun-real) · 리뷰: 반정욱 (@salieri009)
# 서비스: ai-service · 포트 8004 · 의존: discovery, OPEN_AI_KEY (회원 · 매치 조회는 Feign)
# 작업 위치: backend/ai-service/Dockerfile (이 파일이 원본)
# 수정 후: bash scripts/gen-master-dockerfile.sh 를 실행해 backend/Dockerfile(master)도 같이 커밋
# 빌드: docker build -f backend/ai-service/Dockerfile -t sporty/ai-service:local backend

FROM eclipse-temurin:17-jdk AS ai-service-build
WORKDIR /workspace

# 빌드 컨텍스트는 backend/. settings.gradle 이 7개 모듈을 모두 include 하므로 build.gradle 은 전부 필요하다.
COPY . .

# Windows 에서 체크아웃한 gradlew 는 CRLF 라 리눅스에서 실행되지 않으므로 복사 뒤에 줄바꿈을 맞춘다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 캐시 마운트는 sharing=locked: 이미지를 병렬로 빌드해도 Gradle 사용자 홈(/root/.gradle)을 동시에 쓰지 않게 직렬화한다.
# (shared 이면 캐시가 빈 상태에서 Gradle 락 경합으로 'Timeout waiting to lock journal cache' 로 실패한다)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :ai-service:bootJar -x test --no-daemon \
    && cp ai-service/build/libs/ai-service-*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:17-jre AS ai-service
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
WORKDIR /app

RUN useradd --system --uid 1001 sporty
COPY --from=ai-service-build --chown=sporty:sporty /workspace/app.jar app.jar
USER sporty

EXPOSE 8004
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
