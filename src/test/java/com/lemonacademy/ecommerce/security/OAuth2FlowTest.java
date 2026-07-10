package com.lemonacademy.ecommerce.security;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.AuthProvider;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2FlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @InjectMocks
    private OAuth2AuthenticationFailureHandler failureHandler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    @Mock
    private AuthenticationException authenticationException;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "frontendUrl", "https://ecommercef-ten.vercel.app");
        ReflectionTestUtils.setField(failureHandler, "frontendUrl", "https://ecommercef-ten.vercel.app");
        lenient().when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void onAuthenticationSuccess_ExistingUser_GeneratesTokenAndRedirects() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("existing@test.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Existing User");
        when(oAuth2User.getAttribute("picture")).thenReturn("http://existing.com/pic.jpg");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-123");

        User existingUser = User.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .email("existing@test.com")
                .name("Existing User")
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token-123");
        when(response.isCommitted()).thenReturn(false);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrlCaptor.capture());

        String redirectUrl = redirectUrlCaptor.getValue();
        assertTrue(redirectUrl.contains("https://ecommercef-ten.vercel.app/oauth-success"));
        assertTrue(redirectUrl.contains("token=jwt-token-123"));

        verify(userRepository).save(argThat(user -> 
                user.getProvider() == AuthProvider.GOOGLE &&
                "google-sub-123".equals(user.getProviderId()) &&
                "http://existing.com/pic.jpg".equals(user.getProfileImage()) &&
                user.isEmailVerified()
        ));
    }

    @Test
    void onAuthenticationSuccess_NewUser_RegistersAndRedirects() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("new@test.com");
        when(oAuth2User.getAttribute("name")).thenReturn("New User");
        when(oAuth2User.getAttribute("picture")).thenReturn("http://new.com/pic.jpg");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-456");

        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token-456");
        when(response.isCommitted()).thenReturn(false);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrlCaptor.capture());

        String redirectUrl = redirectUrlCaptor.getValue();
        assertTrue(redirectUrl.contains("https://ecommercef-ten.vercel.app/oauth-success"));
        assertTrue(redirectUrl.contains("token=jwt-token-456"));

        verify(userRepository).save(argThat(user -> 
                "new@test.com".equals(user.getEmail()) &&
                "New User".equals(user.getName()) &&
                user.getRole() == Role.CUSTOMER &&
                user.getProvider() == AuthProvider.GOOGLE &&
                "google-sub-456".equals(user.getProviderId()) &&
                "http://new.com/pic.jpg".equals(user.getProfileImage()) &&
                user.isEmailVerified() &&
                user.isActive()
        ));
    }

    @Test
    void onAuthenticationFailure_RedirectsToFailureUrl() throws Exception {
        when(authenticationException.getLocalizedMessage()).thenReturn("OAuth2 error");
        when(response.isCommitted()).thenReturn(false);

        failureHandler.onAuthenticationFailure(request, response, authenticationException);

        ArgumentCaptor<String> redirectUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrlCaptor.capture());

        String redirectUrl = redirectUrlCaptor.getValue();
        System.out.println("--- ACTUAL REDIRECT URL: " + redirectUrl);
        assertTrue(redirectUrl.contains("https://ecommercef-ten.vercel.app/oauth-failure"));
        assertTrue(redirectUrl.contains("error=OAuth2%20error"));
    }

    @Test
    void repository_SavesAndLoadsRequestFromCookies() {
        HttpCookieOAuth2AuthorizationRequestRepository repository = new HttpCookieOAuth2AuthorizationRequestRepository();
        
        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(java.util.Set.of("email", "profile"))
                .state("test-state")
                .build();
                
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        HttpServletResponse mockResp = mock(HttpServletResponse.class);
        
        // 1. Test Saving
        repository.saveAuthorizationRequest(authRequest, mockReq, mockResp);
        
        verify(mockResp, atLeastOnce()).addHeader(eq("Set-Cookie"), anyString());
        
        // 2. Test Loading
        Cookie cookie = new Cookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                CookieUtils.serialize(authRequest));
        when(mockReq.getCookies()).thenReturn(new Cookie[]{cookie});
        
        OAuth2AuthorizationRequest loadedRequest = repository.loadAuthorizationRequest(mockReq);
        
        assertNotNull(loadedRequest);
        assertEquals("test-client", loadedRequest.getClientId());
        assertEquals("test-state", loadedRequest.getState());
        
        // 3. Test Removing
        OAuth2AuthorizationRequest removedRequest = repository.removeAuthorizationRequest(mockReq, mockResp);
        assertNotNull(removedRequest);
        assertEquals("test-client", removedRequest.getClientId());
        verify(mockResp, atLeastOnce()).addHeader(eq("Set-Cookie"), contains("Max-Age=0"));
    }
}
