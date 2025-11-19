
// 1. 카카오 로그인 버튼 클릭
function startKakaoLogin() {
    const redirectUri = window.location.origin + window.location.pathname;
    const authUrl = `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=${KAKAO_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}`;
    window.location.href = authUrl;
}

// 2. 페이지 로드 시 동작
document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get("code");

    if (authCode) {
        handleAuthCode(authCode);
    }
});

// 3. 인증 코드로 토큰 교환 및 로그인 처리
async function handleAuthCode(code) {
    // UI 전환 (로딩 표시)
    document.getElementById("login-btn-area").classList.add("d-none");
    document.getElementById("loading-area").classList.remove("d-none");

    const redirectUri = window.location.origin + window.location.pathname;

    try {
        // 백엔드에 코드와 현재 페이지의 redirectUri를 함께 전송
        const response = await axios.get(`/api/v1/auth/callback/kakao`, {
            params: {
                code: code,
                redirectUri: redirectUri
            }
        });

        // 4. 토큰 저장 및 권한 확인
        const accessToken = response.headers['authorization']; // 헤더에서 토큰 추출
        if (accessToken) {
            const payload = parseJwt(accessToken);

            if (payload.role === 'ADMIN') { // 권한 체크
                localStorage.setItem("accessToken", accessToken);
                localStorage.setItem("adminName", payload.sub); // 이메일 등 저장
                alert("관리자님 환영합니다.");
                window.location.href = "/admin/dashboard";
            } else {
                alert("🚫 관리자 권한이 없는 계정입니다.");
                window.location.href = "/admin/login";
            }
        }

    } catch (error) {
        console.error("Login Failed", error);
        alert("로그인에 실패했습니다.");
        document.getElementById("login-btn-area").classList.remove("d-none");
        document.getElementById("loading-area").classList.add("d-none");
    }
}

// JWT 파싱
function parseJwt (token) {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    return JSON.parse(jsonPayload);
}