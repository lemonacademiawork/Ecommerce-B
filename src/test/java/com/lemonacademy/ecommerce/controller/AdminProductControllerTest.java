package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.ImageUploadResponse;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(AdminProductController.class)
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudinaryService cloudinaryService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User adminUser;
    private User customerUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(2L).email("customer@test.com").role(Role.CUSTOMER).build();
    }

    @Test
    void uploadImage_AsAdmin_Success() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test image content".getBytes());

        when(cloudinaryService.uploadImage(any()))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/test.jpg");

        mockMvc.perform(multipart("/api/admin/products/upload-image")
                        .file(imageFile)
                        .with(user(adminUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").value("https://res.cloudinary.com/demo/image/upload/test.jpg"));
    }

    @Test
    void uploadImage_AsCustomer_Returns403() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test image content".getBytes());

        mockMvc.perform(multipart("/api/admin/products/upload-image")
                        .file(imageFile)
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_Unauthenticated_Returns403() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test image content".getBytes());

        mockMvc.perform(multipart("/api/admin/products/upload-image")
                        .file(imageFile)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_EmptyFile_Returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        when(cloudinaryService.uploadImage(any()))
                .thenThrow(new IllegalArgumentException("File cannot be empty"));

        mockMvc.perform(multipart("/api/admin/products/upload-image")
                        .file(emptyFile)
                        .with(user(adminUser))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
