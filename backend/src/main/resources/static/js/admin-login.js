async function startKakaoLogin() {
    const redirectUri = window.location.origin + window.location.pathname;

    try {
        const stateResponse = await axios.get("/api/v1/auth/state");
        const state = stateResponse.data?.state;

        if (!state) {
            throw new Error("state 발급 실패");
        }

        const authUrl = `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=${KAKAO_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${encodeURIComponent(state)}`;
        window.location.href = authUrl;
    } catch (error) {
        console.error("state 발급 실패", error);
        alert("로그인 준비 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get("code");
    const state = params.get("state");

    if (!authCode) {
        return;
    }

    if (!state) {
        alert("로그인 상태값이 누락되었습니다. 다시 로그인해주세요.");
        window.location.href = "/admin/login";
        return;
    }

    handleAuthCode(authCode, state);
});

async function handleAuthCode(code, state) {
    document.getElementById("login-btn-area").classList.add("d-none");
    document.getElementById("loading-area").classList.remove("d-none");

    const redirectUri = window.location.origin + window.location.pathname;

    try {
        const response = await axios.get("/api/v1/auth/callback/kakao", {
            params: {
                code: code,
                state: state,
                redirectUri: redirectUri
            }
        });

        const accessToken = response.headers["authorization"];
        if (!accessToken) {
            throw new Error("access token 누락");
        }

        const payload = parseJwt(accessToken);
        if (payload.role === "ADMIN") {
            localStorage.setItem("accessToken", accessToken);
            localStorage.setItem("adminName", payload.sub);
            alert("관리자님 환영합니다.");
            window.location.href = "/admin/dashboard";
            return;
        }

        alert("관리자 권한이 없는 계정입니다.");
        window.location.href = "/admin/login";
    } catch (error) {
        console.error("로그인 실패", error);
        alert("로그인에 실패했습니다.");
        document.getElementById("login-btn-area").classList.remove("d-none");
        document.getElementById("loading-area").classList.add("d-none");
    }
}

function parseJwt(token) {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(window.atob(base64).split("").map(function (c) {
        return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(""));
    return JSON.parse(jsonPayload);
}
