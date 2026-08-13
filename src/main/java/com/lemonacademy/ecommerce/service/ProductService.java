package com.lemonacademy.ecommerce.service;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRequestDto;
import com.lemonacademy.ecommerce.dto.ProductVariantResponseDto;
import com.lemonacademy.ecommerce.entity.Category;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.CategoryRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.ProductReviewRepository;
import java.util.Map;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;
    private final UpstashRedisService redisService;
    private final ObjectMapper objectMapper;
    private final ProductReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getAllProducts(Pageable pageable) {
        String cacheKey = makeCacheKey("global-all", pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAll(pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getActiveProducts(Pageable pageable) {
        String cacheKey = makeCacheKey("global-active", pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAllByActiveTrue(pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        ProductResponseDto dto = convertToDto(product);
        populateRatingSummary(dto);
        return dto;
    }

    public static final ThreadLocal<String> CACHE_STATUS = ThreadLocal.withInitial(() -> "MISS");

    private String makeCacheKey(String prefix, List<UUID> categoryIds, Pageable pageable) {
        String ids = categoryIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        return "products:category:" + prefix + ":" + ids + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize() + ":" + pageable.getSort().toString().replaceAll("[\\s:]", "");
    }

    private String makeCacheKey(String prefix, UUID categoryId, Pageable pageable) {
        return makeCacheKey(prefix, List.of(categoryId), pageable);
    }

    private String makeCacheKey(String prefix, Pageable pageable) {
        return "products:category:" + prefix + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize() + ":" + pageable.getSort().toString().replaceAll("[\\s:]", "");
    }

    private PageResponseDto<ProductResponseDto> getCachedOrFetch(String cacheKey, java.util.function.Supplier<PageResponseDto<ProductResponseDto>> dbQuery) {
        CACHE_STATUS.set("MISS");
        try {
            String cachedJson = redisService.get(cacheKey);
            if (cachedJson != null) {
                CACHE_STATUS.set("HIT");
                return objectMapper.readValue(cachedJson, new TypeReference<PageResponseDto<ProductResponseDto>>() {});
            }
        } catch (Exception e) {
            // Fall back gracefully to database
        }
        
        PageResponseDto<ProductResponseDto> data = dbQuery.get();
        
        try {
            String json = objectMapper.writeValueAsString(data);
            redisService.set(cacheKey, json, 300); // 5 min TTL
        } catch (Exception e) {
            // Ignore Redis write failures
        }
        
        return data;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getProductsByCategory(UUID categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        String cacheKey = makeCacheKey("all", categoryId, pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAllByCategoryId(categoryId, pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getActiveProductsByCategory(UUID categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        String cacheKey = makeCacheKey("active", categoryId, pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAllByCategoryIdAndActiveTrue(categoryId, pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getProductsByCategories(List<UUID> categoryIds, Pageable pageable) {
        String cacheKey = makeCacheKey("all-batch", categoryIds, pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAllByCategoryIdIn(categoryIds, pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getActiveProductsByCategories(List<UUID> categoryIds, Pageable pageable) {
        String cacheKey = makeCacheKey("active-batch", categoryIds, pageable);
        return getCachedOrFetch(cacheKey, () -> {
            Page<Product> productPage = productRepository.findAllByCategoryIdInAndActiveTrue(categoryIds, pageable);
            return PageResponseDto.of(productPage, this::convertToDto);
        });
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> searchProducts(String query, Boolean activeOnly, Pageable pageable) {
        Page<Product> productPage = (activeOnly != null && activeOnly)
                ? productRepository.searchActiveProducts(query, pageable)
                : productRepository.searchProducts(query, pageable);
        return PageResponseDto.of(productPage, this::convertToDto);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getProductsByStockStatus(String stockFilter, Boolean activeOnly, Pageable pageable) {
        boolean active = activeOnly != null && activeOnly;
        String normalizedFilter = stockFilter != null ? stockFilter.trim().toUpperCase() : "";
        Page<Product> productPage;

        switch (normalizedFilter) {
            case "OUT_OF_STOCK", "OUTOFSTOCK", "0" -> {
                productPage = active
                        ? productRepository.findActiveOutOfStockProducts(pageable)
                        : productRepository.findOutOfStockProducts(pageable);
            }
            case "LOW_STOCK", "LOWSTOCK", "LOW" -> {
                productPage = active
                        ? productRepository.findActiveLowStockProducts(pageable)
                        : productRepository.findLowStockProducts(pageable);
            }
            case "IN_STOCK", "INSTOCK" -> {
                productPage = active
                        ? productRepository.findActiveInStockProducts(pageable)
                        : productRepository.findInStockProducts(pageable);
            }
            default -> {
                productPage = active
                        ? productRepository.findAllByActiveTrue(pageable)
                        : productRepository.findAll(pageable);
            }
        }
        return PageResponseDto.of(productPage, this::convertToDto);
    }

    @Transactional
    public ProductResponseDto createProduct(ProductRequestDto dto, List<MultipartFile> images) {
        validateImages(images, null);
        
        List<String> uploadedUrls = uploadImages(images);

        try {
            Category category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

            // Merge legacy imageUrl if provided and no images uploaded
            if ((uploadedUrls == null || uploadedUrls.isEmpty()) && dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) {
                uploadedUrls = new ArrayList<>();
                uploadedUrls.add(dto.getImageUrl());
            }

            Product product = Product.builder()
                    .name(dto.getName())
                    .description(dto.getDescription())
                    .price(dto.getPrice())
                    .stock(dto.getStock())
                    .imageUrls(uploadedUrls != null ? uploadedUrls : new ArrayList<>())
                    .active(dto.getActive() != null ? dto.getActive() : true)
                    .hasVariants(dto.getHasVariants() != null ? dto.getHasVariants() : false)
                    .category(category)
                    .weight(dto.getWeightInt())
                    .length(dto.getLengthInt())
                    .breadth(dto.getBreadthInt())
                    .height(dto.getHeightInt())
                    .build();

            Product savedProduct = productRepository.save(product);
            try {
                redisService.deletePattern("products:category:*");
            } catch (Exception e) {
                // ignore
            }
            return convertToDto(savedProduct);
        } catch (Exception e) {
            deleteImages(uploadedUrls);
            throw e;
        }
    }

    // Overloaded method for backward compatibility
    @Transactional
    public ProductResponseDto createProduct(ProductRequestDto dto) {
        return createProduct(dto, null);
    }

    @Transactional
    public ProductResponseDto updateProduct(UUID id, ProductRequestDto dto, List<MultipartFile> newImages) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        List<String> retainedImages = dto.getExistingImageUrls() != null ? dto.getExistingImageUrls() : new ArrayList<>();
        validateImages(newImages, retainedImages);

        List<String> finalUrls = replaceImages(product.getImageUrls(), retainedImages, newImages);

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setImageUrls(finalUrls);
        if (dto.getActive() != null) {
            product.setActive(dto.getActive());
        }
        if (dto.getHasVariants() != null) {
            product.setHasVariants(dto.getHasVariants());
        }
        product.setCategory(category);
        product.setWeight(dto.getWeightInt());
        product.setLength(dto.getLengthInt());
        product.setBreadth(dto.getBreadthInt());
        product.setHeight(dto.getHeightInt());

        Product updatedProduct = productRepository.save(product);
        try {
            redisService.deletePattern("products:category:*");
        } catch (Exception e) {
            // ignore
        }
        return convertToDto(updatedProduct);
    }

    // Overloaded method for backward compatibility
    @Transactional
    public ProductResponseDto updateProduct(UUID id, ProductRequestDto dto) {
        return updateProduct(id, dto, null);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        
        List<String> imagesToDelete = new ArrayList<>(product.getImageUrls());
        productRepository.delete(product);
        deleteImages(imagesToDelete);
        try {
            redisService.deletePattern("products:category:*");
        } catch (Exception e) {
            // ignore
        }
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ProductResponseDto> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Page<Product> productPage = productRepository.findAllByPriceBetweenAndActiveTrue(minPrice, maxPrice, pageable);
        return PageResponseDto.of(productPage, this::convertToDto);
    }

    private void validateImages(List<MultipartFile> newImages, List<String> retainedImages) {
        int newImagesCount = (newImages != null) ? newImages.size() : 0;
        int retainedImagesCount = (retainedImages != null) ? retainedImages.size() : 0;
        int totalImages = newImagesCount + retainedImagesCount;

        // Note: For creation, if they only send a single legacy JSON field, totalImages is 0 here but checked later.
        if (totalImages > 4) {
            throw new IllegalArgumentException("A product can have a maximum of 4 images.");
        }
    }

    private List<String> uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return cloudinaryService.uploadImages(images, "products");
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload images: " + e.getMessage(), e);
        }
    }

    private void deleteImages(List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            cloudinaryService.deleteImages(imageUrls);
        }
    }

    private List<String> replaceImages(List<String> currentImages, List<String> retainedImages, List<MultipartFile> newImages) {
        List<String> finalUrls = new ArrayList<>(retainedImages);
        
        // Find images to delete (current images that are not in retained images)
        List<String> imagesToDelete = new ArrayList<>();
        if (currentImages != null) {
            for (String img : currentImages) {
                if (!retainedImages.contains(img)) {
                    imagesToDelete.add(img);
                }
            }
        }
        
        // Upload new images
        List<String> uploadedUrls = uploadImages(newImages);
        finalUrls.addAll(uploadedUrls);
        
        // Delete orphaned images from Cloudinary
        deleteImages(imagesToDelete);
        
        return finalUrls;
    }

    private ProductResponseDto convertToDto(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .imageUrls(product.getImageUrls())
                .active(product.getActive())
                .hasVariants(product.getHasVariants() != null ? product.getHasVariants() : false)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .weight(product.getWeight())
                .length(product.getLength())
                .breadth(product.getBreadth())
                .height(product.getHeight())
                .variants(product.getVariants() != null ? product.getVariants().stream().map(v -> ProductVariantResponseDto.builder()
                        .id(v.getId())
                        .productId(v.getProduct().getId())
                        .variantName(v.getVariantName())
                        .weight(v.getWeight())
                        .weightUnit(v.getWeightUnit())
                        .volume(v.getVolume())
                        .volumeUnit(v.getVolumeUnit())
                        .sizeLabel(v.getSizeLabel())
                        .price(v.getPrice())
                        .discountedPrice(v.getDiscountedPrice())
                        .stock(v.getStock())
                        .sku(v.getSku())
                        .barcode(v.getBarcode())
                        .status(v.getStatus())
                        .createdAt(v.getCreatedAt())
                        .updatedAt(v.getUpdatedAt())
                        .build()).collect(Collectors.toList()) : new ArrayList<>())
                .shareUrl("https://api.lemonhousecraft.in/api/products/share/" + product.getId())
                .build();
    }

    private void populateRatingSummary(ProductResponseDto dto) {
        try {
            Double avg = reviewRepository.getAverageRatingByProductId(dto.getId());
            Long count = reviewRepository.getReviewCountByProductId(dto.getId());
            List<Object[]> distributionRaw = reviewRepository.getRatingDistributionByProductId(dto.getId());

            Map<Integer, Long> distribution = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                distribution.put(i, 0L);
            }

            if (distributionRaw != null) {
                for (Object[] row : distributionRaw) {
                    Integer rating = (Integer) row[0];
                    Long cnt = (Long) row[1];
                    if (rating != null) {
                        distribution.put(rating, cnt);
                    }
                }
            }

            dto.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null);
            dto.setReviewCount(count != null ? count : 0L);
            dto.setRatingDistribution(distribution);
        } catch (Exception e) {
            // Log and default
            dto.setAverageRating(null);
            dto.setReviewCount(0L);
            dto.setRatingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L));
        }
    }
}
