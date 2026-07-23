package com.lemonacademy.ecommerce.controller;

import java.util.UUID;
import java.util.List;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRequestDto;
import com.lemonacademy.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDto<ProductResponseDto>>> getProducts(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        PageResponseDto<ProductResponseDto> products;
        boolean activeOnly = !all;

        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search, activeOnly, pageable);
        } else if (categoryId != null) {
            products = activeOnly 
                    ? productService.getActiveProductsByCategory(categoryId, pageable) 
                    : productService.getProductsByCategory(categoryId, pageable);
        } else {
            products = activeOnly 
                    ? productService.getActiveProducts(pageable) 
                    : productService.getAllProducts(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductResponseDto>>> searchProducts(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
            
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponseDto<ProductResponseDto> products = productService.searchProducts(keyword, true, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved successfully", products));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductResponseDto>>> filterProducts(
            @RequestParam("minPrice") java.math.BigDecimal minPrice,
            @RequestParam("maxPrice") java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "price") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
            
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponseDto<ProductResponseDto> products = productService.getProductsByPriceRange(minPrice, maxPrice, pageable);
        return ResponseEntity.ok(ApiResponse.success("Filtered products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProductById(@PathVariable UUID id) {
        ProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductResponseDto>>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
            
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        PageResponseDto<ProductResponseDto> products = all 
                ? productService.getProductsByCategory(categoryId, pageable) 
                : productService.getActiveProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved by category successfully", products));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(@Valid @RequestBody ProductRequestDto requestDto) {
        ProductResponseDto createdProduct = productService.createProduct(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", createdProduct));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequestDto requestDto) {
        ProductResponseDto updatedProduct = productService.updateProduct(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updatedProduct));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
