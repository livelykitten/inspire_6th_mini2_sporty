import axios from 'axios';

const endPoint = process.env.REACT_APP_BACKEND_ENDPOINT;
export const commentApi = axios.create({ baseURL: endPoint });
const api = axios.create({ baseURL: endPoint });

api.interceptors.request.use(config => {
  const accessToken = localStorage.getItem('at');
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// [USR-05] 재발급 인증 실패 시에만 세션을 정리
const expireSession = () => {
  localStorage.removeItem('at');
  localStorage.removeItem('rt');
  localStorage.removeItem('userId');
  if (window.location.pathname !== '/login') {
    const from = window.location.pathname + window.location.search;
    window.location.assign('/login?redirect=' + encodeURIComponent(from));
  }
};

// [USR-05] 401 응답 → RT로 AT 재발급 → 실패했던 요청을 한 번만 다시 보냄
api.interceptors.response.use(
  response => response,
  error => {
    const originalRequest = error.config;
    if (!originalRequest || error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // 탈퇴 완료 응답은 토큰 재발급으로 해결되지 않는다.
    if (error.response?.data?.code === 'USER_ALREADY_WITHDRAWN') {
      return Promise.reject(error);
    }

    // 로그인 실패나 로그아웃 요청에서 재발급 X
    const excludedUrls = ['/api/auth/login', '/api/auth/logout', '/api/auth/refresh', '/api/users'];
    if (excludedUrls.includes(originalRequest.url)) return Promise.reject(error);

    const refreshToken = localStorage.getItem('rt');
    if (!refreshToken) {
      expireSession();
      return Promise.reject(error);
    }
    originalRequest._retry = true;

    // 공통 api 대신 axios로 요청하여 재발급 요청이 인터셉터를 반복하지 않게 처리
    return axios.post('/api/auth/refresh', { refreshToken }, { baseURL: endPoint })
      .catch(refreshError => {
        if (refreshError.response?.data?.code === 'INVALID_REFRESH_TOKEN'
            && localStorage.getItem('rt') === refreshToken) {
          expireSession();
        }
        return Promise.reject(refreshError);
      })
      .then(response => {
        // 재발급을 기다리는 동안 로그아웃/계정 변경한 경우 중단
        if (localStorage.getItem('rt') !== refreshToken) return Promise.reject(error);
        localStorage.setItem('at', response.data.accessToken);
        // 요청 인터셉터가 새 AT를 헤더에 붙임
        return api(originalRequest);
      });
  }
);

export default api;
