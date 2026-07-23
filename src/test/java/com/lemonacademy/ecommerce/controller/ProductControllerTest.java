package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRequestDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private ProductResponseDto productResponseDto;
    private List<ProductResponseDto> listResponse;
    private PageResponseDto<ProductResponseDto> pageResponse;
    private User adminUser;

    @BeforeEach
    void setUp() {
        productResponseDto = ProductResponseDto.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Laptop")
                .description("Gaming Laptop")
                .price(new BigDecimal("1500.00"))
                .stock(10)
                .active(true)
                .categoryId(UUID.randomUUID())
                .categoryName("Electronics")
                .build();

        listResponse = Collections.singletonList(productResponseDto);
        pageResponse = PageResponseDto.<ProductResponseDto>builder()
                .content(listResponse)
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        adminUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("admin@test.com").role(Role.ADMIN).build();
    }

    @Test
    void getProducts_Default_Success() throws Exception {
        when(productService.getActiveProducts(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void getProducts_WithSearch_Success() throws Exception {
        when(productService.searchProducts(anyString(), anyBoolean(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products?search=Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void getProducts_ByCategory_Success() throws Exception {
        when(productService.getActiveProductsByCategory(any(UUID.class), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products?categoryId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void getProducts_AllFlag_Success() throws Exception {
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products?all=true"))
                .andExpect(status().isOk());
    }

    @Test
    void searchProducts_Success() throws Exception {
        when(productService.searchProducts(anyString(), anyBoolean(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products/search?keyword=Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void filterProducts_ByPriceRange_Success() throws Exception {
        when(productService.getProductsByPriceRange(any(BigDecimal.class), any(BigDecimal.class), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/products/filter?minPrice=1000&maxPrice=2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void getProductById_Success() throws Exception {
        when(productService.getProductById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(productResponseDto);

        mockMvc.perform(get("/api/products/23db3d7a-683b-372b-8036-95da3ae5c542"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop"));
    }

    @Test
    void getProductById_NotFound_Returns404() throws Exception {
        when(productService.getProductById(UUID.fromString("d2636d80-51bd-3a57-9ac2-4b559df83916")))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductsByCategory_Success() throws Exception {
        when(productService.getActiveProductsByCategory(any(UUID.class), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/products/category/23db3d7a-683b-372b-8036-95da3ae5c542"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
    }

    @Test
    void createProduct_AsAdmin_Success() throws Exception {
        ProductRequestDto request = new ProductRequestDto();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("1500.00"));
        request.setStock(10);
        request.setCategoryId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(productService.createProduct(any(ProductRequestDto.class))).thenReturn(productResponseDto);

        mockMvc.perform(post("/api/products")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Laptop"));
    }

    @Test
    void createProduct_Unauthenticated_Returns403() throws Exception {
        ProductRequestDto request = new ProductRequestDto();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("1500.00"));
        request.setStock(10);
        request.setCategoryId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_AsAdmin_Success() throws Exception {
        ProductRequestDto request = new ProductRequestDto();
        request.setName("Updated Laptop");
        request.setPrice(new BigDecimal("1200.00"));
        request.setStock(5);
        request.setCategoryId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(productService.updateProduct(any(UUID.class), any(ProductRequestDto.class))).thenReturn(productResponseDto);

        mockMvc.perform(put("/api/products/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteProduct_AsAdmin_Success() throws Exception {
        doNothing().when(productService).deleteProduct(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(delete("/api/products/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(adminUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product deleted successfully"));
    }

    @Test
    void deleteProduct_NotFound_Returns404() throws Exception {
        when(productService.getProductById(UUID.fromString("d2636d80-51bd-3a57-9ac2-4b559df83916")))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }
}
