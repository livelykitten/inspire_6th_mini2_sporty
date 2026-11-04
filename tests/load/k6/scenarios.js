// MSA 부하 테스트 시나리오 (이슈 #44). 한 스크립트에서 SCENARIO 로 고른다.
//
//   k6 run tests/load/k6/scenarios.js -e SCENARIO=smoke   -e BASE_URL=http://localhost:8000
//   k6 run tests/load/k6/scenarios.js -e SCENARIO=average -e VUS=20 -e DURATION=5m
//
// SCENARIO: smoke | average | stress | spike | soak | chaos(= average, 장애 주입은 tests/load/chaos.sh 가 컨테이너를 멈춘다)
// 환경변수: BASE_URL(기본 http://localhost:8000) VUS DURATION USERS(사전 가입 계정 수) OUT_DIR(결과 JSON 폴더)
// 같은 스크립트를 1차 모놀리스(BASE_URL=http://localhost:8080)에도 쓸 수 있다 (W-37 비교).
//
// 지표 태그: svc = gateway|match|user|facility|ai, kind = read|write (임계값 · 점수 계산에 쓴다)
// 담당: 반정욱 (@salieri009) · 작업 위치: tests/load/k6/scenarios.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = (__ENV.BASE_URL || 'http://localhost:8000').replace(/\/$/, '');
const SCENARIO = __ENV.SCENARIO || 'smoke';
const VUS = parseInt(__ENV.VUS || (SCENARIO === 'soak' ? '30' : '20'), 10);
const DURATION = __ENV.DURATION || (SCENARIO === 'soak' ? '30m' : '5m');
const USERS = parseInt(__ENV.USERS || String(Math.min(VUS, 20)), 10);
const OUT_DIR = __ENV.OUT_DIR || 'tests/load/results';
const PW = 'Passw0rd!';
const JSON_HDR = { 'Content-Type': 'application/json' };
const SOAK_BUCKET_MS = 10 * 60 * 1000;
const START = Date.now();

function scenarioConfig() {
  switch (SCENARIO) {
    case 'smoke':
      return { executor: 'constant-vus', vus: 1, duration: '30s' };
    case 'stress':
      return {
        executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '15s',
        stages: [
          { duration: '1m', target: 20 }, { duration: '2m', target: 20 },
          { duration: '1m', target: 50 }, { duration: '2m', target: 50 },
          { duration: '1m', target: 100 }, { duration: '2m', target: 100 },
          { duration: '1m', target: 150 }, { duration: '2m', target: 150 },
          { duration: '1m', target: 200 }, { duration: '2m', target: 200 },
          { duration: '30s', target: 0 },
        ],
      };
    case 'spike':
      return {
        executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '15s',
        stages: [
          { duration: '30s', target: 10 }, { duration: '1m', target: 10 },
          { duration: '10s', target: 150 }, { duration: '1m', target: 150 },
          { duration: '10s', target: 10 }, { duration: '1m', target: 10 },
        ],
      };
    case 'soak':
      return { executor: 'constant-vus', vus: VUS, duration: DURATION };
    case 'average':
    case 'chaos':
    default:
      return {
        executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '10s',
        stages: [{ duration: '30s', target: VUS }, { duration: DURATION, target: VUS }, { duration: '15s', target: 0 }],
      };
  }
}

// 임계값. 지연 · 오류율은 읽기/쓰기로 나눠 점수 계산에 쓴다. 스트레스는 오류율 5% 를 넘으면 중단해 "한계 VU" 를 남긴다.
const thresholds = {
  'http_req_duration{kind:read}': ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{kind:write}': ['p(95)<1000'],
  'http_req_failed{kind:read}': [SCENARIO === 'stress' ? { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' } : 'rate<0.01'],
  'http_req_failed{kind:write}': ['rate<0.05'],
};
if (SCENARIO === 'chaos') {
  // 장애 주입 중 판정은 "중지한 서비스(facility)에 의존하지 않는 기능" 만 본다: 매치 읽기 오류율
  thresholds['http_req_failed{svc:match,kind:read}'] = ['rate<0.05'];
}
if (SCENARIO === 'soak') {
  // 10분 구간별 p95 (구간별 서브메트릭을 만들기 위한 항상 참인 임계값). 처음 구간 대비 마지막 구간 증가율을 점수에 쓴다.
  for (let b = 0; b < 12; b++) {
    thresholds[`http_req_duration{bucket:${b}}`] = ['p(95)<999999'];
  }
}

export const options = {
  scenarios: { [SCENARIO]: scenarioConfig() },
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function tags(svc, op, kind) {
  const t = { svc, op, kind };
  if (SCENARIO === 'soak') t.bucket = String(Math.floor((Date.now() - START) / SOAK_BUCKET_MS));
  return { tags: t };
}

function futureIso(hour) {
  const d = new Date(Date.now() + 2 * 86400000);
  d.setUTCHours(hour, 0, 0, 0);
  return d.toISOString().slice(0, 19);
}

function matchBody(title) {
  return JSON.stringify({
    serviceId: 1, title, description: 'k6 load', startAt: futureIso(10), endAt: futureIso(12),
    maxParticipant: 30, skillLevel: 'BEGINNER', sportType: 'FUTSAL', genderGroup: 'MIXED',
  });
}

// 사전 준비: 계정 USERS 개를 가입 · 로그인해 토큰을 재사용하고 (로그인 폭주 방지), 공용 매치 몇 개를 만든다.
export function setup() {
  const stamp = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const email = `k6u${stamp}x${i}@example.com`;
    const signup = http.post(`${BASE}/api/users`, JSON.stringify({
      email, password: PW, gender: 'MALE', nickname: `k6n${String(stamp).slice(-6)}${i}`,
      district: 'GANGNAM', sportTypes: ['FUTSAL'],
    }), { headers: JSON_HDR, tags: { kind: 'setup' } });
    if (signup.status !== 201) continue;
    const login = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: PW }), { headers: JSON_HDR, tags: { kind: 'setup' } });
    const token = login.json('accessToken');
    if (token) tokens.push(token);
  }
  if (tokens.length === 0) throw new Error('사전 계정을 만들지 못했습니다 (시드 데이터 · 서비스 상태 확인)');
  const matchIds = [];
  for (let i = 0; i < 5; i++) {
    const r = http.post(`${BASE}/api/matches`, matchBody(`k6-seed-${stamp}-${i}`), {
      headers: { ...JSON_HDR, Authorization: `Bearer ${tokens[0]}` }, tags: { kind: 'setup' },
    });
    const id = createdId(r);
    if (id) matchIds.push(id);
  }
  return { tokens, matchIds };
}

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
// 매치 생성 응답은 숫자 그대로(1) 이거나 {"matchId":1} 이다
function createdId(res) {
  const body = String(res.body || '').trim();
  if (/^\d+$/.test(body)) return body;
  try { return res.json('matchId'); } catch (e) { return null; }
}
function authed(token, extra) { return { headers: { ...JSON_HDR, Authorization: `Bearer ${token}` }, ...extra }; }

function listMatches() {
  const r = http.get(`${BASE}/api/matches`, tags('match', 'list', 'read'));
  check(r, { 'list 200': (x) => x.status === 200 });
}
function searchMatches() {
  const r = http.get(`${BASE}/api/matches?sportType=FUTSAL`, tags('match', 'search', 'read'));
  check(r, { 'search 200': (x) => x.status === 200 });
}
function matchDetail(d) {
  const r = http.get(`${BASE}/api/matches/${pick(d.matchIds)}`, tags('match', 'detail', 'read'));
  check(r, { 'detail 200': (x) => x.status === 200 });
}
function facilities() {
  const r = http.get(`${BASE}/api/services`, tags('facility', 'list', 'read'));
  check(r, { 'facility list 200': (x) => x.status === 200 });
  const r2 = http.get(`${BASE}/api/services/1`, tags('facility', 'detail', 'read'));
  check(r2, { 'facility detail 200': (x) => x.status === 200 });
}
function me(token) {
  const r = http.get(`${BASE}/api/users/me`, authed(token, tags('user', 'me', 'read')));
  check(r, { 'me 200': (x) => x.status === 200 });
}
function createAndDelete(token) {
  const c = http.post(`${BASE}/api/matches`, matchBody(`k6-${__VU}-${__ITER}`), authed(token, tags('match', 'create', 'write')));
  const ok = check(c, { 'create 201': (x) => x.status === 201 });
  const id = ok ? createdId(c) : null;
  if (id) http.del(`${BASE}/api/matches/${id}`, null, authed(token, tags('match', 'delete', 'write')));
}
function joinAndLeave(token, d) {
  const id = pick(d.matchIds);
  const expected = http.expectedStatuses({ min: 200, max: 299 }, 400, 403, 409); // 이미 참여 · 정원 등은 정상 응답
  const j = http.post(`${BASE}/api/matches/${id}/participants`, null, authed(token, { ...tags('match', 'join', 'write'), responseCallback: expected }));
  if (j.status === 201) http.del(`${BASE}/api/matches/${id}/participants/me`, null, authed(token, { ...tags('match', 'leave', 'write'), responseCallback: expected }));
}

export default function (d) {
  const token = d.tokens[(__VU - 1) % d.tokens.length];
  if (SCENARIO === 'smoke') {
    listMatches(); searchMatches(); matchDetail(d); facilities(); me(token); createAndDelete(token); joinAndLeave(token, d);
    sleep(1);
    return;
  }
  const x = Math.random() * 100;
  if (x < 35) listMatches();
  else if (x < 50) searchMatches();
  else if (x < 70) matchDetail(d);
  else if (x < 80) facilities();
  else if (x < 90) me(token);
  else if (x < 95) createAndDelete(token);
  else joinAndLeave(token, d);
  sleep(Math.random() * 1 + 0.5); // 사용자 생각 시간 0.5 ~ 1.5초
}

// 결과 JSON (점수 계산 입력) 과 짧은 콘솔 요약
export function handleSummary(data) {
  const m = data.metrics;
  const v = (name, stat) => (m[name] && m[name].values ? m[name].values[stat] : undefined);
  const f = (n) => (n === undefined ? '-' : n.toFixed(0));
  const rate = (n) => (n === undefined ? '-' : (n * 100).toFixed(2) + '%');
  const lines = [
    `\n[k6:${SCENARIO}] BASE=${BASE} VUS=${SCENARIO === 'stress' ? 'up to 200' : VUS}`,
    `  요청 ${v('http_reqs', 'count')}건, ${f(v('http_reqs', 'rate'))} req/s, 최대 VU ${f(v('vus_max', 'max'))}`,
    `  읽기  p95 ${f(v('http_req_duration{kind:read}', 'p(95)'))}ms  p99 ${f(v('http_req_duration{kind:read}', 'p(99)'))}ms  오류 ${rate(v('http_req_failed{kind:read}', 'rate'))}`,
    `  쓰기  p95 ${f(v('http_req_duration{kind:write}', 'p(95)'))}ms  오류 ${rate(v('http_req_failed{kind:write}', 'rate'))}`,
  ];
  // 실패한 체크(어느 엔드포인트가 깨졌는지)를 보여 준다
  const failed = ((data.root_group && data.root_group.checks) || []).filter((c) => c.fails > 0);
  failed.forEach((c) => lines.push(`  체크 실패: ${c.name} (${c.fails} / ${c.passes + c.fails})`));
  lines.push('');
  return {
    stdout: lines.join('\n'),
    [`${OUT_DIR}/${SCENARIO}.json`]: JSON.stringify(data),
  };
}
