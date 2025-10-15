package com.backend.domain.member.service;

import com.backend.domain.member.dto.KakaoRegisterResultDto;
import com.backend.domain.member.dto.KakaoUserInfoDto;
import com.backend.domain.member.dto.LoginResponseDto;
import com.backend.domain.member.entity.Member;
import com.backend.domain.member.entity.Role;
import com.backend.domain.member.repository.MemberRepository;
import com.backend.global.auth.entity.MemberDetails;
import com.backend.global.auth.jwt.JwtProvider;
import com.backend.global.auth.refreshtoken.RefreshTokenService;
import com.backend.global.exception.AuthenticationException;
import com.backend.global.exception.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@Slf4j(topic = "KAKAO Login")
@Service
@RequiredArgsConstructor
public class KakaoService {
    private final MemberRepository memberRepository;
    private final RestTemplate restTemplate;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${kakao.client-secret}")
    private String kakaoClientSecret;

    public static final String PROVIDER_KAKAO = "KAKAO";

    @Transactional
    public LoginResponseDto kakaoLogin(String code, HttpServletResponse response) throws IOException {
        log.info("kakao redirect uri : " + kakaoRedirectUri);
        // 1. 카카오 액세스 토큰 가져오기
        String kakaoAccessToken = getToken(code, "http://localhost:8989");

        // 2. 카카오 사용자 정보 가져오기
        KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);

        // 3. 회원가입 필요 여부 확인 및 회원 정보 반환
        KakaoRegisterResultDto kakaoRegisterResultDto = registerKakaoUserIfNeeded(kakaoUserInfo);

        // 4. 로그인 처리
        Member kakaoUser = kakaoRegisterResultDto.getMember();
        forceLogin(kakaoUser);

        // 5. JWT 토큰 생성 및 응답 헤더 설정
        String accessToken = jwtProvider.createAccessToken(kakaoUser.getEmail(), kakaoUser.getRole());
        response.addHeader(JwtProvider.AUTHORIZATION_HEADER, accessToken);

        String refreshToken = jwtProvider.createRefreshToken(kakaoUser.getEmail(), kakaoUser.getRole());
        jwtProvider.addJwtToCookie(refreshToken, response);

        refreshTokenService.insertRefreshToken(kakaoUser.getEmail(), refreshToken.substring(7));

        // 6. LoginResponseDto 구성
        return LoginResponseDto.builder()
                .memberId(kakaoUser.getMember_id())
                .email(kakaoUser.getEmail())
                .isNewMember(kakaoRegisterResultDto.isNewMember())
                .hasMemberInfo(kakaoUser.getName() != null && !kakaoUser.getName().isEmpty())
                .build();
    }

    private String getToken(String code, String redirectUri) throws JsonProcessingException {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://kauth.kakao.com")
                    .path("/oauth/token")
                    .encode()
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("client_id", kakaoClientId);
            // application.yml에 설정된 값이 아닌, 파라미터로 받은 redirectUri를 사용
            body.add("redirect_uri", redirectUri);
            body.add("code", code);
            body.add("client_secret", kakaoClientSecret);

            RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
                    .post(uri)
                    .headers(headers)
                    .body(body);

            ResponseEntity<String> response = restTemplate.exchange(
                    requestEntity,
                    String.class
            );

            JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
            return jsonNode.get("access_token").asText();
        } catch (RestClientException e) {
            // 에러 로깅 개선
            log.error("카카오 토큰 발급 요청 실패", e);
            throw AuthenticationException.socialLoginError();
        }
    }

    private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
        try {
            // 요청 URL 만들기
            URI uri = UriComponentsBuilder
                    .fromUriString("https://kapi.kakao.com")
                    .path("/v2/user/me")
                    .build()
                    .toUri();

            // HTTP Header 생성
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + accessToken);
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

            RequestEntity<Void> requestEntity = RequestEntity
                    .get(uri)
                    .headers(headers)
                    .build();

            // HTTP 요청 보내기
            ResponseEntity<String> response = restTemplate.exchange(
                    requestEntity,
                    String.class
            );

            JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
            String id = jsonNode.get("id").asText();
            String nickname = jsonNode.get("properties")
                    .get("nickname").asText();
            String email = jsonNode.get("kakao_account")
                    .get("email").asText();
            String profileImageUrl = jsonNode.get("kakao_account")
                    .get("profile").get("profile_image_url").asText();

            log.info("카카오 사용자 정보: " + id + ", " + nickname + ", " + email);
            return new KakaoUserInfoDto(id, nickname, email, profileImageUrl);
        } catch (RestClientException e) {
            log.error("유저 데이터 가져오기 실패");
            throw AuthenticationException.fetchUserdataError();
        }
    }

    @Transactional
    protected KakaoRegisterResultDto registerKakaoUserIfNeeded(KakaoUserInfoDto kakaoUserInfo) {
        // 1. 기존 카카오 회원 확인
        Member kakaoUser = memberRepository.findByProviderId(kakaoUserInfo.getId()).orElse(null);
        if (kakaoUser != null) {
            // 이미 회원가입된 카카오 사용자
            return KakaoRegisterResultDto.builder()
                    .isNewMember(false)
                    .member(kakaoUser)
                    .build();
        }

        // 2. 동일 이메일로 다른 SNS 계정이 가입되어 있는지 확인
        Member sameEmailUser = memberRepository.findByEmail(kakaoUserInfo.getEmail()).orElse(null);
        if (sameEmailUser != null) {
            // 계정 중복 가입 불가
            throw ConflictException.emailAlreadyInUse(sameEmailUser.getEmail());
        }

        // 3. 신규 카카오 회원 등록
        kakaoUser = Member.builder()
                .name(kakaoUserInfo.getNickname())
                .email(kakaoUserInfo.getEmail())
                .provider(PROVIDER_KAKAO)
                .providerId(kakaoUserInfo.getId())
                .role(Role.FREE_USER)
                .build();

        Member savedMember = memberRepository.save(kakaoUser);

        return KakaoRegisterResultDto.builder()
                .isNewMember(true)
                .member(savedMember)
                .build();
    }

    private void forceLogin(Member kakaoUser) {
        UserDetails userDetails = new MemberDetails(kakaoUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
