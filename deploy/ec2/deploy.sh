#!/usr/bin/env bash
# EC2 에서 root 로 실행하는 배포 스크립트 (멱등). 순서:
#   저장소 갱신 -> Secrets Manager 로 .env 생성 -> (ECR 로그인 · pull | 빌드) -> compose up -> healthy 대기 -> smoke
#
#   sudo bash /opt/sporty/deploy/ec2/deploy.sh            # 배포 (시크릿을 바꿨거나 코드를 갱신했을 때도 같은 명령)
#   sudo bash /opt/sporty/deploy/ec2/deploy.sh status     # 컨테이너 상태
#   sudo bash /opt/sporty/deploy/ec2/deploy.sh logs gateway-service
#
# 입력: /etc/sporty/aws.env (stack2 user-data 가 만든 비밀이 아닌 값), Secrets Manager (stack1 이 만든 비밀값)
# 담당: 반정욱 (@salieri009) · 작업 위치: deploy/ec2/deploy.sh
set -euo pipefail

REPO_DIR="${REPO_DIR:-/opt/sporty}"
ENV_FILE="${ENV_FILE:-/etc/sporty/aws.env}"

[ -f "$ENV_FILE" ] || { echo "없는 파일: $ENV_FILE (stack2 의 user-data 가 만든다)" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
export AWS_DEFAULT_REGION="$AWS_REGION"
cd "$REPO_DIR"

FILES=(-f compose.yaml -f compose.app.yaml -f compose.aws.yaml)
# awslogs 가 문제를 일으키면 aws.env 에 USE_AWSLOGS=false 를 넣어 로컬 로그만 쓰게 할 수 있다 (로컬 점검에도 사용).
if [ "${USE_AWSLOGS:-true}" = "true" ]; then
  FILES+=(-f compose.awslogs.yaml)
fi
if [ "${DEPLOY_MODE:-build}" = "ecr" ]; then
  FILES+=(-f compose.ecr.yaml)
fi
dc() { docker compose "${FILES[@]}" "$@"; }

cmd="${1:-deploy}"
case "$cmd" in
  status) dc ps --all; exit 0 ;;
  logs) shift; dc logs --tail 200 "$@"; exit 0 ;;
  deploy) ;;
  *) echo "사용법: deploy.sh [deploy|status|logs <서비스>]" >&2; exit 2 ;;
esac

secret_json() { aws secretsmanager get-secret-value --secret-id "$1" --query SecretString --output text; }

# .env 는 단일 따옴표 값으로 쓴다 (compose 가 그대로 읽음). 값에 작은따옴표가 있으면 멈춘다.
q() {
  case "$1" in *"'"*) echo "값에 작은따옴표(')가 있어 .env 로 쓸 수 없습니다" >&2; exit 1 ;; esac
  printf "'%s'" "$1"
}

echo "[deploy] 저장소 갱신 (${GIT_REF})"
if [ -d .git ]; then
  git fetch --depth 1 origin "$GIT_REF"
  git reset --hard FETCH_HEAD
fi

echo "[deploy] Secrets Manager -> .env"
user_pw="$(secret_json "$SECRET_DB_USER_ARN" | jq -r .password)"
match_pw="$(secret_json "$SECRET_DB_MATCH_ARN" | jq -r .password)"
facility_pw="$(secret_json "$SECRET_DB_FACILITY_ARN" | jq -r .password)"
jwt="$(secret_json "$SECRET_JWT_ARN" | jq -r .password)"
encrypt="$(secret_json "$SECRET_ENCRYPT_ARN" | jq -r .password)"
external="$(secret_json "$SECRET_EXTERNAL_ARN")"
openai="$(echo "$external" | jq -r '.OPEN_AI_KEY // ""')"
public_key="$(echo "$external" | jq -r '.PUBLIC_DATA_API_KEY // ""')"
public_url="$(echo "$external" | jq -r '.PUBLIC_DATA_API_URL // ""')"
# 아직 콘솔에서 입력하지 않았으면 서비스가 뜰 수 있게 둔다 (AI 호출 / 시설 동기화만 실패)
if [ "$openai" = "CHANGE_ME" ]; then
  echo "[deploy] 경고: OPEN_AI_KEY 가 아직 입력되지 않았습니다 (AI 호출은 실패합니다)" >&2
  openai=""
fi
if [ "$public_key" = "CHANGE_ME" ]; then
  echo "[deploy] 경고: PUBLIC_DATA_API_KEY 가 아직 입력되지 않았습니다 (시설 동기화는 실패합니다)" >&2
  public_key=""
fi

(
  umask 077
  {
    echo "# deploy.sh 가 만든 파일이다. 직접 고치지 않는다 (Secrets Manager 와 aws.env 가 원본)."
    echo "AWS_REGION=$(q "$AWS_REGION")"
    echo "PROJECT_NAME=$(q "$PROJECT_NAME")"
    echo "ENV_NAME=$(q "$ENV_NAME")"
    echo "ECR_REGISTRY=$(q "${ECR_REGISTRY:-}")"
    echo "IMAGE_TAG=$(q "${IMAGE_TAG:-latest}")"
    echo "USER_RDS_ENDPOINT=$(q "$USER_RDS_ENDPOINT")"
    echo "MATCH_RDS_ENDPOINT=$(q "$MATCH_RDS_ENDPOINT")"
    echo "FACILITY_RDS_ENDPOINT=$(q "$FACILITY_RDS_ENDPOINT")"
    echo "USER_DB_PASSWORD=$(q "$user_pw")"
    echo "MATCH_DB_PASSWORD=$(q "$match_pw")"
    echo "FACILITY_DB_PASSWORD=$(q "$facility_pw")"
    echo "JWT_SECRET_KEY=$(q "$jwt")"
    echo "ENCRYPT_KEY=$(q "$encrypt")"
    echo "OPEN_AI_KEY=$(q "$openai")"
    echo "PUBLIC_DATA_API_KEY=$(q "$public_key")"
    echo "PUBLIC_DATA_API_URL=$(q "$public_url")"
    if [ -n "${CORS_ALLOWED_ORIGIN:-}" ]; then echo "CORS_ALLOWED_ORIGIN=$(q "$CORS_ALLOWED_ORIGIN")"; fi
    if [ -n "${FRONTEND_PORT:-}" ]; then echo "FRONTEND_PORT=$(q "$FRONTEND_PORT")"; fi
  } > .env
)
chmod 600 .env

if [ "${DEPLOY_MODE:-build}" = "ecr" ]; then
  echo "[deploy] ECR 로그인 + pull (${IMAGE_TAG:-latest})"
  aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
  dc pull
  dc up -d
else
  echo "[deploy] EC2 에서 직접 빌드 (첫 빌드는 10~20분)"
  dc up -d --build
fi

# kafka-init 은 토픽을 만들고 종료하는 일회성 컨테이너라 compose --wait 를 쓰지 않고 직접 기다린다.
echo "[deploy] healthy 대기 (최대 15분)"
deadline=$((SECONDS + 900))
bad=""
while [ "$SECONDS" -lt "$deadline" ]; do
  bad="$(dc ps --all --format '{{.Service}} {{.State}} {{.Health}}' | awk '$1 != "kafka-init" && !($2 == "running" && $3 == "healthy")')"
  [ -z "$bad" ] && break
  sleep 10
done
if [ -n "$bad" ]; then
  echo "[deploy] healthy 가 되지 않은 서비스:" >&2
  echo "$bad" >&2
  exit 1
fi

echo "[deploy] smoke"
bash "$REPO_DIR/deploy/ec2/smoke.sh" "http://127.0.0.1"
echo "[deploy] 완료"
