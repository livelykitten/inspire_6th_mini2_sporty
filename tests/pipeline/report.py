#!/usr/bin/env python3
"""MSA 테스트 파이프라인 결과 집계 (이슈 #43).

입력:  results/results.tsv  (스위트 · 점수 · 참조 · 이름 · 상태 · 메시지 · 대기WBS · 초)
       results/suites.txt   (ID|이름|서비스)
출력:  results/results.json, results/summary.md (GitHub Job Summary 에도 쓴다)
점수:  PASS 면 점수 획득, SKIP 은 만점 계산에서 제외, FAIL 은 0점. 합격선(MIN_SCORE) 미만이면 종료 코드 1.
담당: 반정욱 (@salieri009) · 작업 위치: tests/pipeline/report.py
"""
import json
import os
import sys

try:  # 윈도우 콘솔 기본 인코딩(cp949)에서도 한글이 깨지지 않게
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

results_dir = sys.argv[1]
min_score = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0

suites = {}
order = []
with open(os.path.join(results_dir, "suites.txt"), encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n")
        if not line:
            continue
        sid, name, service = line.split("|", 2)
        if sid not in suites:
            suites[sid] = {"id": sid, "name": name, "service": service, "tests": [], "earned": 0, "possible": 0, "skipped": 0}
            order.append(sid)

with open(os.path.join(results_dir, "results.tsv"), encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 8:
            continue
        sid, points, ref, name, status, msg, needs, secs = parts[:8]
        if sid not in suites:
            continue
        pts = int(points)
        s = suites[sid]
        s["tests"].append({"ref": ref, "name": name, "points": pts, "status": status, "message": msg, "needs": needs, "seconds": int(secs)})
        if status == "SKIP":
            s["skipped"] += pts
        else:
            s["possible"] += pts
            if status == "PASS":
                s["earned"] += pts

total_earned = sum(s["earned"] for s in suites.values())
total_possible = sum(s["possible"] for s in suites.values())
total_skipped = sum(s["skipped"] for s in suites.values())
# 만점은 항상 100 으로 보이게 하되, 건너뛴 점수는 따로 표기한다.
score = total_earned
data = {
    "score": score,
    "possible": total_possible,
    "skipped_points": total_skipped,
    "min_score": min_score,
    "passed_threshold": score >= min_score,
    "suites": [suites[k] for k in order],
}
with open(os.path.join(results_dir, "results.json"), "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

icon = {"PASS": "PASS", "FAIL": "FAIL", "SKIP": "SKIP"}
lines = []
lines.append("## MSA 테스트 파이프라인 점수\n")
lines.append(f"**총점 {score} / {total_possible}점**" + (f" (건너뜀 {total_skipped}점 제외)" if total_skipped else "") +
             f" · 합격선 {min_score:g}점 → {'통과' if score >= min_score else '미달'}\n")
lines.append("| 스위트 | 서비스 | 점수 | 통과 / 전체 |")
lines.append("|---|---|---|---|")
for k in order:
    s = suites[k]
    n_pass = sum(1 for t in s["tests"] if t["status"] == "PASS")
    lines.append(f"| {s['id']}. {s['name']} | {s['service']} | {s['earned']} / {s['possible']} | {n_pass} / {len(s['tests'])} |")
lines.append("")
lines.append("### 테스트별 결과\n")
lines.append("| 스위트 | 결과 | 점수 | 시트 TC · 요구사항 | 테스트 | 비고 |")
lines.append("|---|---|---|---|---|---|")
for k in order:
    for t in suites[k]["tests"]:
        note = t["message"].replace("|", "\\|")
        if t["status"] == "FAIL" and t["needs"] and "대기" not in note:
            note = (f"대기: {t['needs']}. " if not note else f"대기: {t['needs']}. ") + note
        lines.append(f"| {k} | {icon[t['status']]} | {t['points'] if t['status'] == 'PASS' else 0} / {t['points']} | {t['ref']} | {t['name']} | {note} |")
md = "\n".join(lines) + "\n"
with open(os.path.join(results_dir, "summary.md"), "w", encoding="utf-8") as f:
    f.write(md)

summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a", encoding="utf-8") as f:
        f.write(md)

print()
print(f"[pipeline] 총점 {score} / {total_possible}" + (f" (건너뜀 {total_skipped}점 제외)" if total_skipped else "") + f"  합격선 {min_score:g}")
for k in order:
    s = suites[k]
    print(f"  {k}. {s['name']:<14} {s['earned']:>3} / {s['possible']:<3}")
sys.exit(0 if score >= min_score else 1)
