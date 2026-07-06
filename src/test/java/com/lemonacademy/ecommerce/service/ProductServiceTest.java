package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRequestDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.entity.Category;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.CategoryRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private Category category;
    private ProductRequestDto productRequestDto;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .active(true)
                .build();

        product = Product.builder()
                .id(1L)
                .name("Laptop")
                .description("Gaming Laptop")
                .price(new BigDecimal("1500.00"))
                .stock(10)
                .imageUrl("http://example.com/laptop.jpg")
                .active(true)
                .category(category)
                .build();

        productRequestDto = new ProductRequestDto();
        productRequestDto.setName("Laptop");
        productRequestDto.setDescription("Gaming Laptop");
        productRequestDto.setPrice(new BigDecimal("1500.00"));
        productRequestDto.setStock(10);
        productRequestDto.setImageUrl("http://example.com/laptop.jpg");
        productRequestDto.setActive(true);
        productRequestDto.setCategoryId(1L);
    }

    @Test
    void getAllProducts_Success() {
        when(productRepository.findAll()).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.getAllProducts();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    void getActiveProducts_Success() {
        when(productRepository.findAllByActiveTrue()).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.getActiveProducts();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void getProductById_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponseDto result = productService.getProductById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getProductById_NotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(1L));
    }

    @Test
    void getProductsByCategory_Success() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findAllByCategoryId(1L)).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.getProductsByCategory(1L);

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void getProductsByCategory_CategoryNotFound() {
        when(categoryRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductsByCategory(1L));
    }

    @Test
    void searchProducts_ActiveOnly() {
        when(productRepository.searchActiveProducts("Lap")).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.searchProducts("Lap", true);

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void searchProducts_All() {
        when(productRepository.searchProducts("Lap")).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.searchProducts("Lap", false);

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void createProduct_Success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponseDto result = productService.createProduct(productRequestDto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Laptop");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_CategoryNotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(productRequestDto));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);

        productRequestDto.setName("Updated Laptop");
        ProductResponseDto result = productService.updateProduct(1L, productRequestDto);

        assertThat(result.getName()).isEqualTo("Updated Laptop");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void updateProduct_ProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(1L, productRequestDto));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void getProductsByPriceRange_Success() {
        BigDecimal minPrice = new BigDecimal("1000.00");
        BigDecimal maxPrice = new BigDecimal("2000.00");

        when(productRepository.findAllByPriceBetweenAndActiveTrue(minPrice, maxPrice)).thenReturn(Collections.singletonList(product));

        java.util.List<ProductResponseDto> response = productService.getProductsByPriceRange(minPrice, maxPrice);

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }
}
