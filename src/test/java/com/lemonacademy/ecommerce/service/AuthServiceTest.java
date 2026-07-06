package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AuthResponse;
import com.lemonacademy.ecommerce.dto.LoginRequest;
import com.lemonacademy.ecommerce.dto.RegisterRequest;
import com.lemonacademy.ecommerce.dto.SendOtpRequest;
import com.lemonacademy.ecommerce.dto.VerifyOtpRequest;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.UserAlreadyExistsException;
import com.lemonacademy.ecommerce.repository.UserRepository;
import com.lemonacademy.ecommerce.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private OtpService otpService;

    @Mock
    private WhatsappService whatsappService;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .phone("+1234567890")
                .password("encodedPassword")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void register_PhoneOnly_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Phone User")
                .phone("+1234567890")
                .build();

        when(userRepository.existsByPhone("+1234567890")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(otpService.generateAndStoreOtp("+1234567890")).thenReturn("123456");

        User registeredUser = authService.register(request);

        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getPhone()).isEqualTo("+1234567890");
        assertThat(registeredUser.getEmail()).isNull();
        assertThat(registeredUser.isActive()).isFalse();

        verify(userRepository, times(1)).save(any(User.class));
        verify(otpService, times(1)).generateAndStoreOtp("+1234567890");
        verify(whatsappService, times(1)).sendOtp("+1234567890", "123456");
    }

    @Test
    void register_EmailOnly_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Email User")
                .email("email@example.com")
                .password("password")
                .build();

        when(userRepository.existsByEmail("email@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        User registeredUser = authService.register(request);

        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getEmail()).isEqualTo("email@example.com");
        assertThat(registeredUser.getPhone()).isNull();
        assertThat(registeredUser.isActive()).isTrue();

        verify(userRepository, times(1)).save(any(User.class));
        verify(otpService, never()).generateAndStoreOtp(anyString());
        verify(whatsappService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void register_DuplicatePhone_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Duplicate Phone")
                .phone("+1234567890")
                .build();

        when(userRepository.existsByPhone("+1234567890")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Duplicate Email")
                .email("email@example.com")
                .password("password")
                .build();

        when(userRepository.existsByEmail("email@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_EmailAndPhoneEmpty_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Invalid Request")
                .build();

        assertThrows(InvalidOperationException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_EmailMissingPassword_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("No Password")
                .email("email@example.com")
                .build();

        when(userRepository.existsByEmail("email@example.com")).thenReturn(false);

        assertThrows(InvalidOperationException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Email_Success() {
        LoginRequest request = LoginRequest.builder()
                .identifier("email@example.com")
                .password("password")
                .build();

        User user = User.builder()
                .name("Email User")
                .email("email@example.com")
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        when(userRepository.findByEmail("email@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("mockJwtToken");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mockJwtToken");
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(otpService, never()).generateAndStoreOtp(anyString());
    }

    @Test
    void login_Email_WrongPassword_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .identifier("email@example.com")
                .password("wrongpassword")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid password"));

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_Phone_Success() {
        LoginRequest request = LoginRequest.builder()
                .identifier("+1234567890")
                .build();

        User user = User.builder()
                .name("Phone User")
                .phone("+1234567890")
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.of(user));
        when(otpService.generateAndStoreOtp("+1234567890")).thenReturn("123456");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNull(); // Token null, generated only after verification
        assertThat(response.getName()).isEqualTo("Phone User");

        verify(authenticationManager, never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(otpService, times(1)).generateAndStoreOtp("+1234567890");
        verify(whatsappService, times(1)).sendOtp("+1234567890", "123456");
    }

    @Test
    void login_Phone_UserNotFound_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .identifier("+1234567890")
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.login(request));
        verify(otpService, never()).generateAndStoreOtp(anyString());
    }

    @Test
    void sendOtp_Success() {
        SendOtpRequest request = new SendOtpRequest();
        request.setPhone("+1234567890");

        when(userRepository.existsByPhone("+1234567890")).thenReturn(true);
        when(otpService.generateAndStoreOtp("+1234567890")).thenReturn("123456");

        authService.sendOtp(request);

        verify(otpService, times(1)).generateAndStoreOtp("+1234567890");
        verify(whatsappService, times(1)).sendOtp("+1234567890", "123456");
    }

    @Test
    void sendOtp_UserNotFound_ThrowsException() {
        SendOtpRequest request = new SendOtpRequest();
        request.setPhone("+1234567890");

        when(userRepository.existsByPhone("+1234567890")).thenReturn(false);

        assertThrows(UsernameNotFoundException.class, () -> authService.sendOtp(request));
        verify(otpService, never()).generateAndStoreOtp(anyString());
    }

    @Test
    void verifyOtpLogin_NewPhoneUser_Success() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhone("+1234567890");
        request.setOtp("123456");

        User user = User.builder()
                .name("New User")
                .phone("+1234567890")
                .role(Role.CUSTOMER)
                .active(false) // Not verified yet
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertThat(saved.isActive()).isTrue(); // Should be set active
            return saved;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mockJwtToken");

        AuthResponse response = authService.verifyOtpLogin(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mockJwtToken");
        verify(otpService, times(1)).verifyAndDeleteOtp("+1234567890", "123456");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void verifyOtpLogin_ExistingPhoneUser_Success() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhone("+1234567890");
        request.setOtp("123456");

        User user = User.builder()
                .name("Existing User")
                .phone("+1234567890")
                .role(Role.CUSTOMER)
                .active(true) // Already verified
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(User.class))).thenReturn("mockJwtToken");

        AuthResponse response = authService.verifyOtpLogin(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mockJwtToken");
        verify(otpService, times(1)).verifyAndDeleteOtp("+1234567890", "123456");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyOtpLogin_WrongOtp_ThrowsException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhone("+1234567890");
        request.setOtp("wrong");

        doThrow(new InvalidOperationException("Invalid OTP"))
                .when(otpService).verifyAndDeleteOtp("+1234567890", "wrong");

        assertThrows(InvalidOperationException.class, () -> authService.verifyOtpLogin(request));
        verify(userRepository, never()).findByPhone(anyString());
    }

    @Test
    void verifyOtpLogin_ExpiredOtp_ThrowsException() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhone("+1234567890");
        request.setOtp("123456");

        doThrow(new InvalidOperationException("OTP expired or not found"))
                .when(otpService).verifyAndDeleteOtp("+1234567890", "123456");

        assertThrows(InvalidOperationException.class, () -> authService.verifyOtpLogin(request));
        verify(userRepository, never()).findByPhone(anyString());
    }

    @Test
    void login_Phone_RedisFailure_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .identifier("+1234567890")
                .build();

        User user = User.builder()
                .name("Phone User")
                .phone("+1234567890")
                .role(Role.CUSTOMER)
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.of(user));
        when(otpService.generateAndStoreOtp("+1234567890")).thenThrow(new RuntimeException("Redis connection failure"));

        assertThrows(RuntimeException.class, () -> authService.login(request));
        verify(whatsappService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void login_Phone_WhatsappFailure_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .identifier("+1234567890")
                .build();

        User user = User.builder()
                .name("Phone User")
                .phone("+1234567890")
                .role(Role.CUSTOMER)
                .build();

        when(userRepository.findByPhone("+1234567890")).thenReturn(Optional.of(user));
        when(otpService.generateAndStoreOtp("+1234567890")).thenReturn("123456");
        doThrow(new RuntimeException("Unable to send OTP via WhatsApp."))
                .when(whatsappService).sendOtp("+1234567890", "123456");

        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
}
