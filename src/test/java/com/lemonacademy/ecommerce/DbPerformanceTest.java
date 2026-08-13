
package com.lemonacademy.ecommerce;

import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductResponseDto;
import com.lemonacademy.ecommerce.entity.Category;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.ProductVariant;
import com.lemonacademy.ecommerce.repository.CategoryRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://ep-young-unit-attjgrrd.c-9.us-east-1.aws.neon.tech/neondb?sslmode=require",
    "spring.datasource.username=neondb_owner",
    "spring.datasource.password=npg_FRqU7CaklmA2",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.show-sql=true",
    "razorpay.key.id=stub-key",
    "razorpay.key.secret=stub-secret",
    "UPSTASH_REDIS_REST_URL=https://stub.upstash.io",
    "UPSTASH_REDIS_REST_TOKEN=stub-token",
    "upi.id=stub-upi",
    "icarry.api-key=stub-key",
    "icarry.username=stub-username",
    "zoepact.api.template-url=https://stub.zoepact.io",
    "zoepact.api.token=stub-token",
    "zoepact.phone-number-id=stub-phone-id",
    "zoepact.template-id=stub-template-id"
})
@Transactional
public class DbPerformanceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    public void measureProductLoadingPerformance() {
        System.out.println("=== STARTING PERFORMANCE SEEDING ===");
        
        // 1. Seed Categories and Products
        Category category = Category.builder()
                .name("Electronics Performance Test")
                .active(true)
                .build();
        category = categoryRepository.save(category);

        List<Product> productsToSave = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Product product = Product.builder()
                    .name("Performance Test Product " + i)
                    .description("High performance product details " + i)
                    .price(new BigDecimal("100.00"))
                    .stock(50)
                    .active(true)
                    .category(category)
                    .imageUrls(new ArrayList<>(Arrays.asList("https://img1.com/" + i, "https://img2.com/" + i)))
                    .build();

            // Add variants
            ProductVariant v1 = ProductVariant.builder()
                    .product(product)
                    .variantName("Size S " + i)
                    .price(new BigDecimal("90.00"))
                    .stock(10)
                    .status(true)
                    .build();
            ProductVariant v2 = ProductVariant.builder()
                    .product(product)
                    .variantName("Size M " + i)
                    .price(new BigDecimal("110.00"))
                    .stock(20)
                    .status(true)
                    .build();
            product.setVariants(new ArrayList<>(Arrays.asList(v1, v2)));
            productsToSave.add(product);
        }
        productRepository.saveAll(productsToSave);
        productRepository.flush();
        entityManager.clear();
        System.out.println("Seeded 100 products with categories, variants, and images.");

        // 2. Measure Performance of getActiveProducts
        System.out.println("=== RUNNING PERFORMANCE MEASUREMENT ===");
        
        long totalStart = System.nanoTime();
        
        // Retrieve products page of size 100 (which will load all seeded products)
        PageResponseDto<ProductResponseDto> result = productService.getActiveProducts(PageRequest.of(0, 100));
        
        long totalEnd = System.nanoTime();
        double totalDurationMs = (totalEnd - totalStart) / 1_000_000.0;
        
        System.out.println("RESULT SIZE: " + result.getContent().size());
        System.out.println("TOTAL ENDPOINT/SERVICE DURATION: " + totalDurationMs + " ms");
        System.out.println("=== END OF PERFORMANCE MEASUREMENT ===");
    }
}
