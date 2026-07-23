package com.lemonacademy.ecommerce.service;

import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

import com.lemonacademy.ecommerce.dto.ProductVariantRequestDto;
import com.lemonacademy.ecommerce.dto.ProductVariantResponseDto;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.ProductVariant;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ProductVariantResponseDto addVariant(UUID productId, ProductVariantRequestDto dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .variantName(dto.getVariantName())
                .weight(dto.getWeight())
                .weightUnit(dto.getWeightUnit())
                .volume(dto.getVolume())
                .volumeUnit(dto.getVolumeUnit())
                .sizeLabel(dto.getSizeLabel())
                .price(dto.getPrice())
                .discountedPrice(dto.getDiscountedPrice())
                .stock(dto.getStock())
                .sku(dto.getSku())
                .barcode(dto.getBarcode())
                .status(dto.getStatus() != null ? dto.getStatus() : true)
                .build();

        ProductVariant savedVariant = productVariantRepository.save(variant);
        return convertToDto(savedVariant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponseDto> getVariantsByProductId(UUID productId) {
        return productVariantRepository.findByProductId(productId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ProductVariantResponseDto> getActiveVariantsByProductId(UUID productId) {
        return productVariantRepository.findByProductIdAndStatusTrue(productId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductVariantResponseDto updateVariant(UUID id, ProductVariantRequestDto dto) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant not found with id: " + id));

        variant.setVariantName(dto.getVariantName());
        variant.setWeight(dto.getWeight());
        variant.setWeightUnit(dto.getWeightUnit());
        variant.setVolume(dto.getVolume());
        variant.setVolumeUnit(dto.getVolumeUnit());
        variant.setSizeLabel(dto.getSizeLabel());
        variant.setPrice(dto.getPrice());
        variant.setDiscountedPrice(dto.getDiscountedPrice());
        variant.setStock(dto.getStock());
        variant.setSku(dto.getSku());
        variant.setBarcode(dto.getBarcode());
        if (dto.getStatus() != null) {
            variant.setStatus(dto.getStatus());
        }

        ProductVariant updatedVariant = productVariantRepository.save(variant);
        return convertToDto(updatedVariant);
    }

    @Transactional
    public void deleteVariant(UUID id) {
        if (!productVariantRepository.existsById(id)) {
            throw new ResourceNotFoundException("ProductVariant not found with id: " + id);
        }
        productVariantRepository.deleteById(id);
    }

    private ProductVariantResponseDto convertToDto(ProductVariant v) {
        return ProductVariantResponseDto.builder()
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
                .build();
    }
}
