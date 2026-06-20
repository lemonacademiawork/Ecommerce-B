package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.ImageUploadResponse;
import com.lemonacademy.ecommerce.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Products", description = "Administrative Product Management APIs")
public class AdminProductController {

    private final CloudinaryService cloudinaryService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image to Cloudinary")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @RequestParam("image") MultipartFile image) throws IOException {
        String imageUrl = cloudinaryService.uploadImage(image);
        ImageUploadResponse response = ImageUploadResponse.builder().imageUrl(imageUrl).build();
        return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", response));
    }
}
