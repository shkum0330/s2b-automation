// 1. Axios 전역 설정: 백엔드 도메인과 동일하므로 기본값 사용
axios.defaults.baseURL = '/';

// 2. [중요] 요청 인터셉터: localStorage의 토큰을 꺼내 헤더에 주입
axios.interceptors.request.use(function (config) {
    const token = localStorage.getItem('accessToken');
    if (token) {
        config.headers['Authorization'] = token;
    }
    return config;
}, function (error) {
    return Promise.reject(error);
});

// 3. 응답 인터셉터: 토큰 만료(401)나 권한 없음(403) 시 로그인 페이지로 강제 이동
axios.interceptors.response.use(function (response) {
    return response;
}, function (error) {
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
        alert('로그인 세션이 만료되었습니다.\n다시 로그인해주세요.');
        logout();
    }
    return Promise.reject(error);
});

// 4. 로그아웃 함수
function logout() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('adminName');
    window.location.href = '/admin/login';
}

// 5. 페이지 진입 시 로그인 체크 (로그인 페이지 제외)
if (window.location.pathname !== '/admin/login') {
    if (!localStorage.getItem('accessToken')) {
        window.location.href = '/admin/login';
    } else {
        // 사이드바에 관리자 이름 표시 (있을 경우)
        const adminName = localStorage.getItem('adminName');
        const nameEl = document.getElementById('adminName');
        if (nameEl && adminName) nameEl.textContent = adminName;
    }
}