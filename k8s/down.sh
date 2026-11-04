#!/usr/bin/env bash
# 클러스터에서 Sporty 를 지운다 (네임스페이스째, 데이터 포함). 이미지는 남는다.
set -euo pipefail
kubectl delete namespace sporty --wait=true
