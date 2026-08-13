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
import org.springframework.http.MediaType;
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
            @RequestParam(value = "stockFilter", required = false) String stockFilterParam,
            @RequestParam(value = "stockStatus", required = false) String stockStatusParam,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        String sanitizedSort = sanitizeSortBy(sortBy);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sanitizedSort).ascending()
                : Sort.by(sanitizedSort).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        PageResponseDto<ProductResponseDto> products;
        boolean activeOnly = !all;
        String stockFilter = stockFilterParam != null ? stockFilterParam : stockStatusParam;

        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search, activeOnly, pageable);
        } else if (stockFilter != null && !stockFilter.trim().isEmpty()) {
            products = productService.getProductsByStockStatus(stockFilter, activeOnly, pageable);
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
            
        String sanitizedSort = sanitizeSortBy(sortBy);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sanitizedSort).ascending() : Sort.by(sanitizedSort).descending();
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
            
        String sanitizedSort = sanitizeSortBy(sortBy);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sanitizedSort).ascending() : Sort.by(sanitizedSort).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponseDto<ProductResponseDto> products = productService.getProductsByPriceRange(minPrice, maxPrice, pageable);
        return ResponseEntity.ok(ApiResponse.success("Filtered products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProductById(@PathVariable UUID id) {
        ProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @GetMapping(value = "/share/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getProductSharePreview(@PathVariable UUID id) {
        ProductResponseDto product = productService.getProductById(id);
        String desc = product.getDescription() != null ? product.getDescription() : "";
        
        // Remove the [IMAGES:...] block from the description for a cleaner preview
        desc = desc.replaceAll("\\[IMAGES:[^\\]]+\\]", "").trim();
        
        String frontendUrl = "https://lemonhousecraft.in/product/" + product.getId();
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html><head>\n")
            .append("<meta charset=\"UTF-8\">\n")
            .append("<meta property=\"og:title\" content=\"").append(product.getName()).append("\" />\n")
            .append("<meta property=\"og:description\" content=\"").append(desc).append("\" />\n")
            .append("<meta property=\"og:url\" content=\"").append(frontendUrl).append("\" />\n");
            
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            html.append("<meta property=\"og:image\" content=\"").append(product.getImageUrl()).append("\" />\n");
        }
            
        html.append("<title>").append(product.getName()).append("</title>\n")
            // Automatically redirect real users to the frontend product page
            .append("<script>window.location.replace(\"").append(frontendUrl).append("\");</script>\n")
            .append("<meta http-equiv=\"refresh\" content=\"0;url=").append(frontendUrl).append("\">\n")
            .append("</head><body>\n")
            .append("<p>Redirecting to <a href=\"").append(frontendUrl).append("\">product page</a>...</p>\n")
            .append("</body></html>");
            
        return ResponseEntity.ok(html.toString());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductResponseDto>>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
            
        String sanitizedSort = sanitizeSortBy(sortBy);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sanitizedSort).ascending() : Sort.by(sanitizedSort).descending();
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


    private String sanitizeSortBy(String sortBy) {
        if (sortBy == null) {
            return "createdAt";
        }
        return switch (sortBy.toLowerCase()) {
            case "name" -> "name";
            case "price" -> "price";
            case "stock" -> "stock";
            case "createdat", "created_at" -> "createdAt";
            case "updatedat", "updated_at" -> "updatedAt";
            case "id" -> "id";
            default -> "createdAt";
        };
    }
}
