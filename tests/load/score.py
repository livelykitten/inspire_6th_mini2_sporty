#!/usr/bin/env python3
"""부하 테스트 점수 계산 (이슈 #44). 시나리오별 k6 결과 JSON 을 읽어 100점 만점으로 환산한다.

항목                    점수  판정
지연시간 (L2 평균 부하)   30   읽기 p95 < 500ms (20) / 쓰기 p95 < 1000ms (10) — 초과 시 1.5배 이내면 절반
오류율                   25   L2 읽기 오류율 < 1% (15), L3 스트레스 오류율 < 5% (10)
확장성 (L3)              20   오류율 5% 를 넘기 전 도달한 VU: <50 5점, <100 10점, <150 15점, 그 이상 20점
복원력 (L6)              15   장애 중 오류율 < 5% (10) · 재기동 뒤 60초 안에 복구 (5)
안정성 (L5)              10   10분 구간 p95 증가 +20% 이내 (5) · 메모리 증가 +20% 이내 (5, memory.csv 가 있을 때)

측정하지 않은 항목은 "측정 안 함" 으로 두고 총점 분모에서 제외한다. 입력: tests/load/results/*.json
사용: python tests/load/score.py [결과 폴더]      담당: 반정욱 (@salieri009)
"""
import json
import os
import sys

try:  # 윈도우 콘솔 기본 인코딩(cp949)에서도 한글이 깨지지 않게
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

d = sys.argv[1] if len(sys.argv) > 1 else "tests/load/results"


def load(name):
    p = os.path.join(d, name + ".json")
    if not os.path.exists(p):
        return None
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def val(data, metric, stat):
    m = data["metrics"].get(metric)
    if not m:
        return None
    return (m.get("values") or m).get(stat)


rows = []  # (항목, 얻은 점수, 만점, 근거)


def add(name, got, full, why):
    rows.append((name, got, full, why))


def graded(value, limit, full):
    """limit 이내 full, 1.5배 이내 절반, 그 외 0"""
    if value is None:
        return None
    if value < limit:
        return full
    if value < limit * 1.5:
        return full // 2
    return 0


avg = load("average")
if avg:
    rp95 = val(avg, "http_req_duration{kind:read}", "p(95)")
    wp95 = val(avg, "http_req_duration{kind:write}", "p(95)")
    g = graded(rp95, 500, 20)
    add("지연시간: 읽기 p95", g, 20, f"{rp95:.0f}ms (목표 < 500ms)" if rp95 is not None else "데이터 없음")
    g = graded(wp95, 1000, 10)
    add("지연시간: 쓰기 p95", g, 10, f"{wp95:.0f}ms (목표 < 1000ms)" if wp95 is not None else "데이터 없음")
    er = val(avg, "http_req_failed{kind:read}", "rate")
    add("오류율: L2 읽기", (15 if er is not None and er < 0.01 else 0) if er is not None else None, 15,
        f"{er * 100:.2f}% (목표 < 1%)" if er is not None else "데이터 없음")
else:
    add("지연시간 · L2 오류율", None, 55, "average 시나리오 결과 없음")

stress = load("stress")
if stress:
    er = val(stress, "http_req_failed{kind:read}", "rate")
    maxvu = val(stress, "vus", "max") or val(stress, "vus_max", "max")
    add("오류율: L3 스트레스", (10 if er is not None and er < 0.05 else 0) if er is not None else None, 10,
        f"{er * 100:.2f}% (목표 < 5%)" if er is not None else "데이터 없음")
    if maxvu is not None:
        pts = 5 if maxvu < 50 else 10 if maxvu < 100 else 15 if maxvu < 150 else 20
        add("확장성: 한계 VU", pts, 20, f"오류율 5% 초과 전 최대 {maxvu:.0f} VU")
else:
    add("L3 스트레스 (오류율 · 확장성)", None, 30, "stress 시나리오 결과 없음")

chaos = load("chaos")
if chaos:
    # 중지한 서비스(facility)에 의존하지 않는 매치 읽기만 본다 (시설 API 자체는 멈춘 동안 실패하는 것이 정상)
    er = val(chaos, "http_req_failed{svc:match,kind:read}", "rate")
    add("복원력: 장애 중 매치 읽기 오류율", (10 if er is not None and er < 0.05 else 0) if er is not None else None, 10,
        f"{er * 100:.2f}% (목표 < 5%, 시설 API 제외)" if er is not None else "데이터 없음")
    rec_path = os.path.join(d, "chaos-recovery.txt")
    if os.path.exists(rec_path):
        rec = float(open(rec_path).read().strip() or -1)
        add("복원력: 재기동 뒤 복구", 5 if 0 <= rec <= 60 else 0, 5, f"{rec:.0f}s (목표 60s 이내)")
    else:
        add("복원력: 재기동 뒤 복구", None, 5, "chaos-recovery.txt 없음")
else:
    add("L6 장애 주입 (복원력)", None, 15, "chaos 시나리오 결과 없음")

soak = load("soak")
if soak:
    p95 = []
    for b in range(12):
        v = val(soak, f"http_req_duration{{bucket:{b}}}", "p(95)")
        if v is not None:
            p95.append(v)
    if len(p95) >= 2:
        growth = (p95[-1] - p95[0]) / p95[0] if p95[0] else 0
        add("안정성: p95 증가", 5 if growth <= 0.2 else 0, 5, f"첫 구간 {p95[0]:.0f}ms → 마지막 {p95[-1]:.0f}ms ({growth * 100:+.0f}%)")
    else:
        add("안정성: p95 증가", None, 5, "10분 구간이 2개 미만 (소크 시간이 짧음)")
    mem = os.path.join(d, "memory.csv")
    if os.path.exists(mem):
        rows_m = [l.strip().split(",") for l in open(mem) if l.strip()]
        try:
            first, last = float(rows_m[0][1]), float(rows_m[-1][1])
            growth = (last - first) / first if first else 0
            add("안정성: 메모리 증가", 5 if growth <= 0.2 else 0, 5, f"{first:.0f} → {last:.0f} MiB ({growth * 100:+.0f}%)")
        except Exception:
            add("안정성: 메모리 증가", None, 5, "memory.csv 형식 오류")
    else:
        add("안정성: 메모리 증가", None, 5, "memory.csv 없음")
else:
    add("L5 소크 (안정성)", None, 10, "soak 시나리오 결과 없음")

got = sum(r[1] for r in rows if r[1] is not None)
full = sum(r[2] for r in rows if r[1] is not None)
unmeasured = sum(r[2] for r in rows if r[1] is None)

print("| 항목 | 점수 | 근거 |")
print("|---|---|---|")
for name, g, f, why in rows:
    print(f"| {name} | {'측정 안 함' if g is None else f'{g} / {f}'} | {why} |")
print()
print(f"**부하 테스트 점수: {got} / {full}** (측정하지 않은 {unmeasured}점 제외, 만점 100)")
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as fh:
        fh.write("## 부하 테스트 점수\n\n| 항목 | 점수 | 근거 |\n|---|---|---|\n")
        for name, g, f, why in rows:
            fh.write(f"| {name} | {'측정 안 함' if g is None else f'{g} / {f}'} | {why} |\n")
        fh.write(f"\n**{got} / {full}** (측정하지 않은 {unmeasured}점 제외, 만점 100)\n")
