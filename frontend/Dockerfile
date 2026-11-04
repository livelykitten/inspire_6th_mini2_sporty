# syntax=docker/dockerfile:1
# 담당: 반정욱 (@salieri009) · 프론트 코드 담당은 팀 시트 확인 후 추가
# 서비스: frontend (nginx :80) · 의존: gateway-service:8000 (nginx 가 /api/ 를 프록시)
# 작업 위치: frontend/Dockerfile, frontend/nginx.conf (이 파일이 원본, master 대상 아님)
# 빌드: docker build -t sporty/frontend:local frontend
#
# 1단계 배포 구조와 같다: ALB 는 이 nginx 로만 보내고, nginx 가 / 는 정적 파일로, /api/ 는 gateway 로 보낸다 (로컬 · AWS 동일).

FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

# 빈 값이면 frontend/src/api/axios.js 의 baseURL 이 상대 경로가 되어 같은 오리진(nginx)으로 요청한다.
# 다른 주소로 빌드하려면 --build-arg REACT_APP_BACKEND_ENDPOINT=https://... 를 준다.
ARG REACT_APP_BACKEND_ENDPOINT=
ENV REACT_APP_BACKEND_ENDPOINT=$REACT_APP_BACKEND_ENDPOINT
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
