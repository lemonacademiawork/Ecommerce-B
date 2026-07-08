package com.lemonacademy.ecommerce.security;

import com.lemonacademy.ecommerce.entity.AuthProvider;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend.url:https://ecommercef-ten.vercel.app}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String sub = oAuth2User.getAttribute("sub");

        if (email == null) {
            getRedirectStrategy().sendRedirect(request, response, 
                UriComponentsBuilder.fromUriString(frontendUrl + "/oauth-failure")
                        .queryParam("error", "Email not found from Google provider")
                        .build().toUriString());
            return;
        }

        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    // Update Google-specific details if not already set
                    if (existingUser.getProvider() == null || existingUser.getProvider() == AuthProvider.LOCAL) {
                        existingUser.setProvider(AuthProvider.GOOGLE);
                        existingUser.setProviderId(sub);
                    }
                    if (picture != null && existingUser.getProfileImage() == null) {
                        existingUser.setProfileImage(picture);
                    }
                    existingUser.setEmailVerified(true);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .name(name != null ? name : email.split("@")[0])
                            .email(email)
                            .role(Role.CUSTOMER)
                            .provider(AuthProvider.GOOGLE)
                            .providerId(sub)
                            .profileImage(picture)
                            .emailVerified(true)
                            .active(true)
                            .build();
                    return userRepository.save(newUser);
                });

        String token = jwtService.generateToken(user);

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth-success")
                .queryParam("token", token)
                .build().encode().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
