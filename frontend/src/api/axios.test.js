let api;
let adapter;
const failure = (config, status, code) => Promise.reject({ config, response: { status, data: { code } }, isAxiosError: true });
const success = (config, data) => Promise.resolve({ config, status: 200, data, headers: {} });

beforeEach(() => {
  jest.resetModules();
  localStorage.clear();
  window.history.replaceState({}, '', '/login');
  localStorage.setItem('at', 'old-at');
  localStorage.setItem('rt', 'existing-rt');
  adapter = jest.fn();
  require('axios').default.defaults.adapter = adapter;
  api = require('./axios').default;
});
afterEach(() => localStorage.clear());

test('401이면 새 AT로 원래 요청을 재시도한다', async () => {
  adapter.mockImplementation(config => {
    if (config.url === '/api/auth/refresh') return success(config, { accessToken: 'new-at' });
    return config.headers.Authorization === 'Bearer new-at' ? success(config, 'ok') : failure(config, 401);
  });
  const result = await api.get('/api/users/me');
  expect(result.data).toBe('ok');
  const refreshCalls = adapter.mock.calls.filter(([config]) => config.url === '/api/auth/refresh');
  expect(refreshCalls).toHaveLength(1);
  expect(JSON.parse(refreshCalls[0][0].data)).toEqual({ refreshToken: 'existing-rt' });
  expect(localStorage.getItem('at')).toBe('new-at');
  expect(localStorage.getItem('rt')).toBe('existing-rt');
});

test.each(['/api/auth/login', '/api/auth/logout', '/api/auth/refresh'])('%s는 자동 재발급에서 제외한다', async path => {
  adapter.mockImplementation(config => failure(config, 401));
  await expect(api.post(path)).rejects.toBeDefined();
  expect(adapter).toHaveBeenCalledTimes(1);
});

test('재발급 후에도 401이면 반복 요청하지 않는다', async () => {
  adapter.mockImplementation(config => config.url === '/api/auth/refresh'
    ? success(config, { accessToken: 'new-at' }) : failure(config, 401));
  await expect(api.get('/api/users/me')).rejects.toBeDefined();
  expect(adapter).toHaveBeenCalledTimes(3);
});

test('RT 불일치 401이면 로그인 정보를 삭제한다', async () => {
  adapter.mockImplementation(config => failure(config, 401, 'INVALID_REFRESH_TOKEN'));
  await expect(api.get('/api/users/me')).rejects.toBeDefined();
  expect(localStorage.getItem('at')).toBeNull();
  expect(localStorage.getItem('rt')).toBeNull();
});

test('오류 코드 없는 401이면 RT 인증 실패로 단정하지 않는다', async () => {
  adapter.mockImplementation(config => failure(config, 401));
  await expect(api.get('/api/users/me')).rejects.toBeDefined();
  expect(localStorage.getItem('at')).toBe('old-at');
  expect(localStorage.getItem('rt')).toBe('existing-rt');
});

test('재발급 서버 장애에는 세션을 유지한다', async () => {
  adapter.mockImplementation(config => failure(config, config.url === '/api/auth/refresh' ? 503 : 401));
  await expect(api.get('/api/users/me')).rejects.toBeDefined();
  expect(localStorage.getItem('rt')).toBe('existing-rt');
});

test('재발급 중 로그아웃하면 뒤늦은 응답으로 세션을 복원하지 않는다', async () => {
  adapter.mockImplementation(config => {
    if (config.url === '/api/auth/refresh') {
      localStorage.clear();
      return success(config, { accessToken: 'new-at' });
    }
    return failure(config, 401);
  });
  await expect(api.get('/api/users/me')).rejects.toBeDefined();
  expect(localStorage.getItem('at')).toBeNull();
  expect(adapter).toHaveBeenCalledTimes(2);
});

test.each(['get', 'delete'])(
  '탈퇴 완료 401은 %s 요청에서 재발급 없이 원래 오류를 전달한다',
  async method => {
    localStorage.setItem('userId', '6');

    adapter.mockImplementation(config =>
      failure(config, 401, 'USER_ALREADY_WITHDRAWN')
    );

    await expect(
      api.request({
        method,
        url: '/api/users/me'
      })
    ).rejects.toMatchObject({
      response: {
        status: 401,
        data: {
          code: 'USER_ALREADY_WITHDRAWN'
        }
      }
    });

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(
      adapter.mock.calls.some(
        ([config]) => config.url === '/api/auth/refresh'
      )
    ).toBe(false);

    expect(localStorage.getItem('at')).toBe('old-at');
    expect(localStorage.getItem('rt')).toBe('existing-rt');
    expect(localStorage.getItem('userId')).toBe('6');
  }
);