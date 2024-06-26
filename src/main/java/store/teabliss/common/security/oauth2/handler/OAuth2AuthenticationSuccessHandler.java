package store.teabliss.common.security.oauth2.handler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import store.teabliss.common.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import store.teabliss.common.security.oauth2.service.OAuth2UserPrincipal;
import store.teabliss.common.security.oauth2.user.OAuth2Provider;
import store.teabliss.common.security.oauth2.user.OAuth2UserUnlinkManager;
import store.teabliss.common.security.signin.service.JwtService;
import store.teabliss.common.security.utils.CookieUtils;
import store.teabliss.member.entity.Member;
import store.teabliss.member.exception.NotFoundMemberByEmailException;
import store.teabliss.member.mapper.MemberMapper;

import java.io.IOException;
import java.util.Optional;

import static store.teabliss.common.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository.MODE_PARAM_COOKIE_NAME;
import static store.teabliss.common.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME;

@Slf4j
@RequiredArgsConstructor
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final OAuth2UserUnlinkManager oAuth2UserUnlinkManager;
    private final JwtService jwtService;
    private final MemberMapper memberMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {

        Optional<String> redirectUri = CookieUtils.getCookie(request, REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(Cookie::getValue);

        String targetUrl = redirectUri.orElse(getDefaultTargetUrl());

        String mode = CookieUtils.getCookie(request, MODE_PARAM_COOKIE_NAME)
                .map(Cookie::getValue)
                .orElse("");

        OAuth2UserPrincipal principal = getOAuth2UserPrincipal(authentication);

        if (principal == null) {
            return UriComponentsBuilder.fromUriString(targetUrl)
                    .queryParam("error", "Login failed")
                    .build().toUriString();
        }

        if ("signIn".equalsIgnoreCase(mode)) {
            // 액세스 토큰, 리프레시 토큰 발급
            String accessToken = jwtService.createAccessToken(principal.getUserInfo().getEmail());
            String refreshToken = jwtService.createRefreshToken();

            String email = principal.getName();

            // 리프레시 토큰 DB 저장
            Member member = memberMapper.findByEmail(email).orElseThrow(
                    () -> new NotFoundMemberByEmailException(email)
            );
            member.updateRefreshToken(refreshToken);
            memberMapper.updateRefreshToken(member);

            jwtService.sendAccessAndRefreshToken(response, accessToken, refreshToken);

            return UriComponentsBuilder.fromUriString(targetUrl)
                    .build().toUriString();

        } else if ("signOut".equalsIgnoreCase(mode)) {

            String accessToken = principal.getUserInfo().getAccessToken();
            OAuth2Provider provider = principal.getUserInfo().getProvider();

            String email = principal.getName();

            // 리프레시 토큰 삭제
            Member member = memberMapper.findByEmail(email).orElseThrow(
                    () -> new NotFoundMemberByEmailException(email)
            );
            member.destroyRefreshToken();
            memberMapper.updateRefreshToken(member);

            oAuth2UserUnlinkManager.unlink(provider, accessToken);

            return UriComponentsBuilder.fromUriString(targetUrl)
                    .build().toUriString();
        }

        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("error", "Login failed")
                .build().toUriString();
    }

    private OAuth2UserPrincipal getOAuth2UserPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2UserPrincipal) {
            return (OAuth2UserPrincipal) principal;
        }
        return null;
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}
