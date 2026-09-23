package com.repomind.config;

import com.repomind.model.User;
import com.repomind.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            OAuth2AuthorizedClientService authorizedClientService,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.authorizedClientService = authorizedClientService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName());
        String accessToken = client.getAccessToken().getTokenValue();

        
        Long githubId = Long.valueOf(oAuth2User.getAttribute("id").toString());
        String username = oAuth2User.getAttribute("login");
        String name = oAuth2User.getAttribute("name");
        String email = oAuth2User.getAttribute("email");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");

        
        User user = userRepository.findByGithubId(githubId)
                .map(existingUser -> {
                    existingUser.setUsername(username);
                    existingUser.setName(name);
                    existingUser.setEmail(email);
                    existingUser.setAvatarUrl(avatarUrl);
                    existingUser.setAccessToken(accessToken);
                    existingUser.setLastLoginAt(LocalDateTime.now());
                    return existingUser;
                })
                .orElseGet(() -> User.builder()
                        .githubId(githubId)
                        .username(username)
                        .name(name)
                        .email(email)
                        .avatarUrl(avatarUrl)
                        .accessToken(accessToken)
                        .build());

        userRepository.save(user);

        
        String jwt = tokenProvider.generateToken(githubId, username, accessToken);

        
        String redirectUrl = frontendUrl + "/auth/callback?token=" + jwt;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
