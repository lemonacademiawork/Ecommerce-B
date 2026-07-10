package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.CategoryDto;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private CategoryDto categoryDto;
    private User adminUser;
    private User customerUser;

    @BeforeEach
    void setUp() {
        categoryDto = CategoryDto.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Electronics")
                .imageUrl("http://example.com/img.jpg")
                .active(true)
                .build();

        adminUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("admin@test.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf")).email("customer@test.com").role(Role.CUSTOMER).build();

        List<CategoryDto> listResponse = Collections.singletonList(categoryDto);
        when(categoryService.getActiveCategories()).thenReturn(listResponse);
        when(categoryService.getAllCategories()).thenReturn(listResponse);
    }

    @Test
    void getAllCategories_ActiveOnly_Success() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Electronics"));
    }

    @Test
    void getAllCategories_AllFlag_Success() throws Exception {
        mockMvc.perform(get("/api/categories?all=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCategoryById_Success() throws Exception {
        when(categoryService.getCategoryById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(categoryDto);

        mockMvc.perform(get("/api/categories/23db3d7a-683b-372b-8036-95da3ae5c542"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }

    @Test
    void getCategoryById_NotFound_Returns404() throws Exception {
        when(categoryService.getCategoryById(UUID.fromString("d2636d80-51bd-3a57-9ac2-4b559df83916")))
                .thenThrow(new ResourceNotFoundException("Category not found with id: 99"));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCategory_AsAdmin_Success() throws Exception {
        CategoryDto request = CategoryDto.builder().name("Electronics").active(true).build();
        when(categoryService.createCategory(any(CategoryDto.class))).thenReturn(categoryDto);

        mockMvc.perform(post("/api/categories")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }

    @Test
    void createCategory_AsCustomer_Returns403() throws Exception {
        CategoryDto request = CategoryDto.builder().name("Electronics").active(true).build();

        mockMvc.perform(post("/api/categories")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_Unauthenticated_Returns403() throws Exception {
        CategoryDto request = CategoryDto.builder().name("Electronics").active(true).build();

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_DuplicateName_Returns400() throws Exception {
        CategoryDto request = CategoryDto.builder().name("Electronics").active(true).build();
        when(categoryService.createCategory(any(CategoryDto.class)))
                .thenThrow(new IllegalArgumentException("Category with name 'Electronics' already exists."));

        mockMvc.perform(post("/api/categories")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCategory_AsAdmin_Success() throws Exception {
        CategoryDto updateRequest = CategoryDto.builder().name("Updated Electronics").active(true).build();
        CategoryDto updated = CategoryDto.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).name("Updated Electronics").active(true).build();
        when(categoryService.updateCategory(any(UUID.class), any(CategoryDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/categories/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Electronics"));
    }

    @Test
    void deleteCategory_AsAdmin_Success() throws Exception {
        doNothing().when(categoryService).deleteCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(delete("/api/categories/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(adminUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Category deleted successfully"));
    }

    @Test
    void deleteCategory_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Category not found with id: 1"))
                .when(categoryService).deleteCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(delete("/api/categories/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(adminUser))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
