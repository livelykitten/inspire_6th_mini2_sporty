#!/usr/bin/env bash
# EC2(Amazon Linux 2023) 최초 설정: docker · compose · buildx 플러그인 · git · jq · CloudWatch Agent.
# stack2 의 user-data 가 clone 직후 한 번 실행한다. 다시 실행해도 안전하다 (멱등).
#
# 담당: 반정욱 (@salieri009) · 작업 위치: deploy/ec2/bootstrap.sh
# 버전을 바꾸려면: COMPOSE_VERSION=v2.x.y BUILDX_VERSION=v0.x.y bash deploy/ec2/bootstrap.sh
set -euo pipefail

REPO_DIR="${REPO_DIR:-/opt/sporty}"
COMPOSE_VERSION="${COMPOSE_VERSION:-v2.38.2}"
BUILDX_VERSION="${BUILDX_VERSION:-v0.26.1}"
PLUGIN_DIR=/usr/local/lib/docker/cli-plugins

echo "[bootstrap] 패키지 설치"
dnf install -y docker git jq amazon-cloudwatch-agent

echo "[bootstrap] docker 데몬 설정 (awslogs 를 쓰지 않는 컨테이너의 로컬 로그 크기 제한)"
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
fi
systemctl enable --now docker
systemctl restart docker

# 공식 릴리스에서 내려받고, 체크섬 파일을 받을 수 있으면 검증한다.
fetch_plugin() { # 이름 URL 체크섬URL
  local name="$1" url="$2" sum_url="$3" tmp
  mkdir -p "$PLUGIN_DIR"
  tmp="$(mktemp)"
  curl -fsSL "$url" -o "$tmp"
  if curl -fsSL "$sum_url" -o "$tmp.sha256" 2>/dev/null; then
    local want got
    want="$(awk '{print $1}' "$tmp.sha256" | head -1)"
    got="$(sha256sum "$tmp" | awk '{print $1}')"
    [ "$want" = "$got" ] || { echo "체크섬 불일치: $name" >&2; exit 1; }
    echo "[bootstrap] $name 체크섬 확인"
  else
    echo "[bootstrap] $name 체크섬 파일을 받지 못해 검증을 건너뜀" >&2
  fi
  install -m 0755 "$tmp" "$PLUGIN_DIR/$name"
  rm -f "$tmp" "$tmp.sha256"
}

if ! docker compose version 2>/dev/null | grep -q "${COMPOSE_VERSION#v}"; then
  echo "[bootstrap] docker compose ${COMPOSE_VERSION} 설치"
  base="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}"
  fetch_plugin docker-compose "$base/docker-compose-linux-x86_64" "$base/docker-compose-linux-x86_64.sha256"
fi
if ! docker buildx version 2>/dev/null | grep -q "${BUILDX_VERSION}"; then
  echo "[bootstrap] docker buildx ${BUILDX_VERSION} 설치"
  base="https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}"
  fetch_plugin docker-buildx "$base/buildx-${BUILDX_VERSION}.linux-amd64" "$base/buildx-${BUILDX_VERSION}.linux-amd64.sha256"
fi
docker compose version
docker buildx version

echo "[bootstrap] CloudWatch Agent (메모리 · 디스크 지표)"
cp "$REPO_DIR/deploy/ec2/cwagent.json" /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s

echo "[bootstrap] 완료"
