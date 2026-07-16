package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.ImageUploadResponse;
import com.lemonacademy.ecommerce.dto.ProductRequestDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.service.CloudinaryService;
import com.lemonacademy.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Products", description = "Administrative Product Management APIs")
public class AdminProductController {

    private final CloudinaryService cloudinaryService;
    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new product with multiple images")
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(
            @Valid @ModelAttribute ProductRequestDto requestDto,
            @Parameter(description = "Up to 4 images for the product", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "image", required = false) MultipartFile legacyImage) throws IOException {

        List<MultipartFile> finalImages = normalizeImages(images, legacyImage);
        ProductResponseDto createdProduct = productService.createProduct(requestDto, finalImages);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", createdProduct));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update product details and images")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable UUID id,
            @Valid @ModelAttribute ProductRequestDto requestDto,
            @Parameter(description = "New images to add to the product", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "image", required = false) MultipartFile legacyImage) throws IOException {

        List<MultipartFile> finalImages = normalizeImages(images, legacyImage);
        ProductResponseDto updatedProduct = productService.updateProduct(id, requestDto, finalImages);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updatedProduct));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product and its images")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image to Cloudinary (Legacy)")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @RequestParam("image") MultipartFile image) throws IOException {
        String imageUrl = cloudinaryService.uploadImage(image);
        ImageUploadResponse response = ImageUploadResponse.builder()
                .imageUrl(imageUrl)
                .imageUrls(List.of(imageUrl))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", response));
    }
    
    private List<MultipartFile> normalizeImages(List<MultipartFile> images, MultipartFile legacyImage) {
        List<MultipartFile> finalImages = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            finalImages.addAll(images);
        } else if (legacyImage != null && !legacyImage.isEmpty()) {
            finalImages.add(legacyImage);
        }
        return finalImages;
    }
}
