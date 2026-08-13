package com.lemonacademy.ecommerce.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;

import com.lemonacademy.ecommerce.dto.ProductReviewRequestDto;
import com.lemonacademy.ecommerce.dto.ProductReviewResponseDto;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.ProductReview;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.ProductReviewRepository;
import com.lemonacademy.ecommerce.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProductReviewServiceTest {

    @Mock
    private ProductReviewRepository reviewRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private UpstashRedisService redisService;

    @InjectMocks
    private ProductReviewServiceImpl reviewService;

    private UUID productId;
    private UUID userId;
    private User customer;
    private Product product;
    private ProductReview review;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        userId = UUID.randomUUID();

        customer = User.builder()
                .id(userId)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.CUSTOMER)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Lemon Candle")
                .build();

        review = ProductReview.builder()
                .id(UUID.randomUUID())
                .product(product)
                .user(customer)
                .rating(5)
                .comment("Excellent quality!")
                .verifiedPurchase(true)
                .build();
    }

    @Test
    void testCanUserReview_Success() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)).thenReturn(false);
        when(orderRepository.hasUserPurchasedProductAndDelivered(userId, productId)).thenReturn(true);

        boolean result = reviewService.canUserReview(productId, userId);

        assertTrue(result);
    }

    @Test
    void testCanUserReview_NoPurchase_ReturnsFalse() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)).thenReturn(false);
        when(orderRepository.hasUserPurchasedProductAndDelivered(userId, productId)).thenReturn(false);

        boolean result = reviewService.canUserReview(productId, userId);

        assertFalse(result);
    }

    @Test
    void testCreateReview_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)).thenReturn(false);
        when(orderRepository.hasUserPurchasedProductAndDelivered(userId, productId)).thenReturn(true);

        ProductReviewRequestDto request = ProductReviewRequestDto.builder()
                .rating(4)
                .comment("Pretty good")
                .build();

        when(reviewRepository.save(any(ProductReview.class))).thenAnswer(invocation -> {
            ProductReview pr = invocation.getArgument(0);
            pr.setId(UUID.randomUUID());
            return pr;
        });

        ProductReviewResponseDto response = reviewService.createReview(productId, userId, request, null);

        assertNotNull(response);
        assertEquals(4, response.getRating());
        assertEquals("Pretty good", response.getComment());
        assertTrue(response.getVerifiedPurchase());
        verify(reviewRepository, times(1)).save(any(ProductReview.class));
    }

    @Test
    void testCreateReview_DuplicateReview_ThrowsException() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)).thenReturn(true);

        ProductReviewRequestDto request = ProductReviewRequestDto.builder()
                .rating(4)
                .build();

        assertThrows(InvalidOperationException.class, () -> {
            reviewService.createReview(productId, userId, request, null);
        });
    }

    @Test
    void testCreateReview_InvalidRating_ThrowsException() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)).thenReturn(false);
        when(orderRepository.hasUserPurchasedProductAndDelivered(userId, productId)).thenReturn(true);

        ProductReviewRequestDto requestLow = ProductReviewRequestDto.builder().rating(0).build();
        ProductReviewRequestDto requestHigh = ProductReviewRequestDto.builder().rating(6).build();

        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createReview(productId, userId, requestLow, null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.createReview(productId, userId, requestHigh, null);
        });
    }

    @Test
    void testUpdateReview_Success() {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        ProductReviewRequestDto request = ProductReviewRequestDto.builder()
                .rating(3)
                .comment("Changed my mind, ok quality")
                .build();

        when(reviewRepository.save(any(ProductReview.class))).thenReturn(review);

        ProductReviewResponseDto response = reviewService.updateReview(review.getId(), userId, request, null, null);

        assertNotNull(response);
        assertEquals(3, response.getRating());
        assertEquals("Changed my mind, ok quality", response.getComment());
    }

    @Test
    void testUpdateReview_Unauthorized_ThrowsException() {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        ProductReviewRequestDto request = ProductReviewRequestDto.builder()
                .rating(3)
                .build();

        UUID anotherUserId = UUID.randomUUID();

        assertThrows(UnauthorizedAccessException.class, () -> {
            reviewService.updateReview(review.getId(), anotherUserId, request, null, null);
        });
    }

    @Test
    void testDeleteReview_SuccessByOwner() {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        reviewService.deleteReview(review.getId(), userId, false);

        assertNotNull(review.getDeletedAt());
        verify(reviewRepository, times(1)).save(review);
    }

    @Test
    void testDeleteReview_SuccessByAdmin() {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        reviewService.deleteReview(review.getId(), null, true);

        assertNotNull(review.getDeletedAt());
        verify(reviewRepository, times(1)).save(review);
    }

    @Test
    void testDeleteReview_Unauthorized_ThrowsException() {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        UUID anotherUserId = UUID.randomUUID();

        assertThrows(UnauthorizedAccessException.class, () -> {
            reviewService.deleteReview(review.getId(), anotherUserId, false);
        });
    }
}
