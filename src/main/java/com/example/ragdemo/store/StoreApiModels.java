package com.example.ragdemo.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoreApiModels {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StoreApiModels() {
    }

    public record SkuResponse(Long id, String skuCode, Map<String, String> specValues,
            BigDecimal price, BigDecimal originalPrice, Integer stock, Integer sales,
            String imageUrl, Boolean enabled) {

        static SkuResponse from(StoreSku sku) {
            return new SkuResponse(sku.getId(), sku.getSkuCode(), parseSpecs(sku.getSpecValues()),
                    sku.getPrice(), sku.getOriginalPrice(), sku.getStock(), sku.getSales(),
                    sku.getImageUrl(), sku.getEnabled());
        }
    }

    public record ProductDetailResponse(ProductResponse product, List<SkuResponse> skus) {
    }

    public record AddCartItemRequest(Long skuId, Integer quantity) {
    }

    public record UpdateCartItemRequest(Integer quantity) {
    }

    public record CartItemResponse(Long id, Long skuId, Long productId, String productName,
            String skuCode, Map<String, String> specValues, BigDecimal price,
            BigDecimal originalPrice, Integer stock, String imageUrl, Integer quantity,
            BigDecimal subtotal, Boolean enabled) {

        static CartItemResponse from(StoreCartItem item) {
            StoreSku sku = item.getSku();
            return new CartItemResponse(item.getId(), sku.getId(), sku.getProduct().getId(),
                    sku.getProduct().getName(), sku.getSkuCode(), parseSpecs(sku.getSpecValues()),
                    sku.getPrice(), sku.getOriginalPrice(), sku.getStock(), sku.getImageUrl(),
                    item.getQuantity(), sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                    sku.getEnabled());
        }
    }

    public record CartResponse(List<CartItemResponse> items, Integer totalQuantity,
            BigDecimal totalAmount) {
    }

    public record CreateOrderRequest(List<Long> cartItemIds, Long addressId, List<Long> userCouponIds) {
        public CreateOrderRequest(List<Long> cartItemIds, Long addressId) {
            this(cartItemIds, addressId, List.of());
        }
    }

    public record CheckoutPreviewRequest(List<Long> cartItemIds, Long addressId,
            List<Long> userCouponIds) {
        public CheckoutPreviewRequest(List<Long> cartItemIds, Long addressId) {
            this(cartItemIds, addressId, List.of());
        }
    }

    public record CouponResponse(Long id, String code, String name, String type,
            BigDecimal thresholdAmount, BigDecimal discountAmount, BigDecimal discountRate,
            Boolean stackable, Long productId, String category, String status) { }

    public record AppliedCouponResponse(Long userCouponId, String name, BigDecimal discountAmount) { }

    public record CheckoutPreviewResponse(BigDecimal originalAmount, BigDecimal discountAmount,
            BigDecimal payableAmount, List<AppliedCouponResponse> coupons,
            List<AppliedCouponResponse> recommendedCoupons, BigDecimal recommendedDiscountAmount,
            BigDecimal recommendedPayableAmount) { }

    public record CsrfResponse(String headerName, String token) {
    }

    public record RegisterRequest(String username, String password, String displayName) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record UpdateProfileRequest(String displayName, String phone) {
    }

    public record UserResponse(Long id, String username, String displayName, String phone,
            Instant createdAt) {

        static UserResponse from(StoreUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getPhone(), user.getCreatedAt());
        }
    }

    public record SaveAddressRequest(String receiverName, String receiverPhone, String province,
            String city, String district, String detailAddress, String postalCode,
            Boolean defaultAddress) {
    }

    public record AddressResponse(Long id, String receiverName, String receiverPhone,
            String province, String city, String district, String detailAddress,
            String postalCode, Boolean defaultAddress, String fullAddress) {

        static AddressResponse from(StoreAddress address) {
            return new AddressResponse(address.getId(), address.getReceiverName(),
                    address.getReceiverPhone(), address.getProvince(), address.getCity(),
                    address.getDistrict(), address.getDetailAddress(), address.getPostalCode(),
                    address.getDefaultAddress(), address.fullAddress());
        }
    }

    public record OrderItemResponse(Long id, Long productId, Long skuId, String productName,
            String skuCode, Map<String, String> specValues, BigDecimal price, Integer quantity,
            BigDecimal subtotal, String imageUrl) {

        static OrderItemResponse from(StoreOrderItem item) {
            return new OrderItemResponse(item.getId(), item.getProductId(), item.getSkuId(),
                    item.getProductName(), item.getSkuCode(), parseSpecs(item.getSpecValues()),
                    item.getPrice(), item.getQuantity(), item.getSubtotal(), item.getImageUrl());
        }
    }

    public record OrderResponse(Long id, String orderNo, String status, BigDecimal totalAmount,
            BigDecimal discountAmount, BigDecimal payableAmount, List<AppliedCouponResponse> coupons,
            String receiverName, String receiverPhone, String receiverAddress, Instant createdAt,
            Instant updatedAt, String paymentNo, Instant paidAt, Instant cancelledAt,
            List<OrderItemResponse> items) {

        static OrderResponse from(StoreOrder order) {
            return new OrderResponse(order.getId(), order.getOrderNo(), order.getStatus(),
                    order.getTotalAmount(), order.getDiscountAmount(), order.getPayableAmount(),
                    order.getCoupons().stream().map(c -> new AppliedCouponResponse(c.getUserCouponId(),
                            c.getCouponName(), c.getDiscountAmount())).toList(),
                    order.getReceiverName(), order.getReceiverPhone(),
                    order.getReceiverAddress(), order.getCreatedAt(), order.getUpdatedAt(),
                    order.getPaymentNo(), order.getPaidAt(), order.getCancelledAt(), order.getItems().stream()
                            .map(OrderItemResponse::from).toList());
        }
    }

    static Map<String, String> parseSpecs(String value) {
        try {
            return JSON.readValue(value, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception ignored) {
            return Map.of("规格", value);
        }
    }
}
