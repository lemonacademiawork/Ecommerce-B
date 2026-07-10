package com.lemonacademy.ecommerce.service;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.CategoryDto;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.entity.Category;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.CategoryRepository;
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

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private CategoryDto categoryDto;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Electronics")
                .imageUrl("http://example.com/electronics.jpg")
                .active(true)
                .build();

        categoryDto = CategoryDto.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Electronics")
                .imageUrl("http://example.com/electronics.jpg")
                .active(true)
                .build();
    }

    @Test
    void getAllCategories_Success() {
        when(categoryRepository.findAll()).thenReturn(Collections.singletonList(category));

        java.util.List<CategoryDto> response = categoryService.getAllCategories();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getName()).isEqualTo("Electronics");
    }

    @Test
    void getActiveCategories_Success() {
        when(categoryRepository.findAllByActiveTrue()).thenReturn(Collections.singletonList(category));

        java.util.List<CategoryDto> response = categoryService.getActiveCategories();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void getCategoryById_Success() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(category));

        CategoryDto result = categoryService.getCategoryById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void getCategoryById_NotFound() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")));
    }

    @Test
    void createCategory_Success() {
        when(categoryRepository.existsByName(anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryDto result = categoryService.createCategory(categoryDto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Electronics");
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void createCategory_DuplicateName() {
        when(categoryRepository.existsByName(anyString())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(categoryDto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_Success() {
        CategoryDto updateDto = CategoryDto.builder()
                .name("New Electronics")
                .imageUrl("http://example.com/new.jpg")
                .active(false)
                .build();

        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot(anyString(), any(UUID.class))).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArguments()[0]);

        CategoryDto result = categoryService.updateCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"), updateDto);

        assertThat(result.getName()).isEqualTo("New Electronics");
        assertThat(result.getImageUrl()).isEqualTo("http://example.com/new.jpg");
        assertThat(result.getActive()).isFalse();
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void updateCategory_NotFound() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"), categoryDto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_DuplicateName() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot(anyString(), any(UUID.class))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> categoryService.updateCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"), categoryDto));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteCategory_Success() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(category));

        categoryService.deleteCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        verify(categoryRepository, times(1)).delete(category);
    }

    @Test
    void deleteCategory_NotFound() {
        when(categoryRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")));
        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
