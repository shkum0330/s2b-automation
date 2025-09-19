package com.backend.global.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends BaseException {

    private static final String UNAUTHENTICATED_TOKEN = "토큰 '%s'는 유효하지 않습니다.";
    private static final String SOCIAL_LOGIN_ERROR = "카카오 인증에 실패하였습니다.";
    private static final String NO_USER_ROLE = "사용자 역할 정보가 없습니다.";
    private static final String NO_REFRESH_TOKEN = "리프레시 토큰이 없습니다.";
    private static final String FETCH_USERDATA_ERROR = "카카오에서 사용자 정보를 가져오던 중 문제가 발생하였습니다.";

    /**
     * Creates an AuthenticationException with HTTP 401 (UNAUTHORIZED) and the provided detail message.
     *
     * @param message human-readable description of the authentication error
     */
    public AuthenticationException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * Creates an AuthenticationException for an invalid or unauthenticated token.
     *
     * @param token the token value that was determined to be invalid (included in the exception message)
     * @return an AuthenticationException with HTTP 401 (UNAUTHORIZED) and a formatted message identifying the token
     */
    public static AuthenticationException unauthenticatedToken(String token) {
        return new AuthenticationException(String.format(UNAUTHENTICATED_TOKEN, token));
    }

    /**
     * Creates an AuthenticationException for Kakao social login failures.
     *
     * @return an AuthenticationException with HTTP 401 (UNAUTHORIZED) and the message indicating Kakao authentication failure
     */
    public static AuthenticationException socialLoginError() {
        return new AuthenticationException(SOCIAL_LOGIN_ERROR);
    }

    /**
     * Creates an AuthenticationException indicating the user has no role assigned.
     *
     * @return an AuthenticationException with HTTP 401 (UNAUTHORIZED) and the message "사용자 역할 정보가 없습니다."
     */
    public static AuthenticationException noUserRole() {
        return new AuthenticationException(NO_USER_ROLE);
    }

    /**
     * Creates an AuthenticationException representing a missing refresh token (HTTP 401).
     *
     * @return an AuthenticationException with the message "리프레시 토큰이 없습니다."
     */
    public static AuthenticationException noRefreshToken() {
        return new AuthenticationException(NO_REFRESH_TOKEN);
    }

    /**
     * Create an AuthenticationException for errors encountered while fetching user data from Kakao.
     *
     * @return an AuthenticationException with HTTP 401 (UNAUTHORIZED) and a message indicating a failure to retrieve user information from Kakao
     */
    public static AuthenticationException fetchUserdataError() {
        return new AuthenticationException(FETCH_USERDATA_ERROR);
    }
}
