package com.example.ragdemo.store;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StoreOrderService {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StoreOrderRepository orderRepository;
    private final StoreCartItemRepository cartRepository;
    private final StoreSkuRepository skuRepository;
    private final CurrentStoreUser currentUser;
    private final StoreAddressService addressService;
    private final StoreUserCouponRepository userCouponRepository;
    private final StoreCouponRecommendationService couponRecommendationService;

    public StoreOrderService(StoreOrderRepository orderRepository, StoreCartItemRepository cartRepository,
            StoreSkuRepository skuRepository, CurrentStoreUser currentUser,
            StoreAddressService addressService, StoreUserCouponRepository userCouponRepository,
            StoreCouponRecommendationService couponRecommendationService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.skuRepository = skuRepository;
        this.currentUser = currentUser;
        this.addressService = addressService;
        this.userCouponRepository = userCouponRepository;
        this.couponRecommendationService = couponRecommendationService;
    }

    @Transactional
    public StoreApiModels.OrderResponse create(StoreApiModels.CreateOrderRequest request) {
        validate(request);
        StoreAddress address = addressService.ownedAddress(request.addressId());
        List<Long> itemIds = List.copyOf(new LinkedHashSet<>(request.cartItemIds()));
        List<StoreCartItem> cartItems = cartRepository.findByIdInAndUserId(itemIds, currentUser.userId());
        if (cartItems.size() != itemIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分购物车项不存在或不属于当前用户");
        }

        List<Long> skuIds = cartItems.stream().map(item -> item.getSku().getId()).sorted().toList();
        Map<Long, StoreSku> lockedSkus = new HashMap<>();
        skuRepository.findAllByIdForUpdate(skuIds).forEach(sku -> lockedSkus.put(sku.getId(), sku));
        if (lockedSkus.size() != skuIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "部分SKU已不存在");
        }

        StoreCouponContext couponContext = couponContext(cartItems, lockedSkus);
        List<StoreUserCoupon> availableCoupons = userCouponRepository == null
                || request.userCouponIds() == null || request.userCouponIds().isEmpty()
                        ? List.of()
                        : userCouponRepository.findOwnedByIdsForUpdate(currentUser.userId(), request.userCouponIds());
        validateCoupons(availableCoupons, request.userCouponIds());
        StoreCouponPlan couponPlan = recommend(couponContext, availableCoupons);

        StoreOrder order = new StoreOrder(nextOrderNo(), currentUser.userId(), address.getReceiverName(),
                address.getReceiverPhone(), address.fullAddress());
        for (StoreCartItem cartItem : cartItems) {
            StoreSku sku = lockedSkus.get(cartItem.getSku().getId());
            if (!Boolean.TRUE.equals(sku.getEnabled())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, sku.getSkuCode() + "已下架");
            }
            if (sku.getStock() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        sku.getSkuCode() + "库存不足，当前库存" + sku.getStock());
            }
            order.addItem(new StoreOrderItem(sku, cartItem.getQuantity()));
            sku.decreaseStock(cartItem.getQuantity());
        }

        for (StoreCouponCandidate candidate : couponPlan.candidates()) {
            candidate.userCoupon().use();
            order.addCoupon(new StoreOrderCoupon(candidate.userCoupon(), candidate.discountAmount()));
        }

        StoreOrder saved = orderRepository.save(order);
        cartRepository.deleteAll(cartItems);
        return StoreApiModels.OrderResponse.from(saved);
    }

    public StoreApiModels.CheckoutPreviewResponse preview(StoreApiModels.CheckoutPreviewRequest request) {
        validate(request);
        List<Long> itemIds = List.copyOf(new LinkedHashSet<>(request.cartItemIds()));
        List<StoreCartItem> cartItems = cartRepository.findByIdInAndUserId(itemIds, currentUser.userId());
        if (cartItems.size() != itemIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分购物车项不存在或不属于当前用户");
        }
        List<Long> requestedCouponIds = request.userCouponIds() == null
                ? List.of() : List.copyOf(new LinkedHashSet<>(request.userCouponIds()));
        List<StoreUserCoupon> coupons = requestedCouponIds.isEmpty() ? List.of()
                : userCouponRepository.findByUserIdAndIdIn(currentUser.userId(), requestedCouponIds);
        validateCoupons(coupons, requestedCouponIds);
        StoreCouponContext context = couponContext(cartItems, Map.of());
        StoreCouponPlan plan = recommend(context, coupons);
        List<StoreUserCoupon> allCoupons = userCouponRepository.findByUserIdAndStatus(
                currentUser.userId(), StoreUserCoupon.UNUSED).stream().filter(this::couponUsable).toList();
        StoreCouponPlan recommended = recommend(context, allCoupons);
        return new StoreApiModels.CheckoutPreviewResponse(context.originalAmount(), plan.discountAmount(),
                plan.payableAmount(), appliedCoupons(plan), appliedCoupons(recommended),
                recommended.discountAmount(), recommended.payableAmount());
    }

    private List<StoreApiModels.AppliedCouponResponse> appliedCoupons(StoreCouponPlan plan) {
        return plan.candidates().stream().map(c -> new StoreApiModels.AppliedCouponResponse(
                c.userCoupon().getId(), c.userCoupon().getCoupon().getName(), c.discountAmount())).toList();
    }

    private StoreCouponPlan recommend(StoreCouponContext context, List<StoreUserCoupon> coupons) {
        if (couponRecommendationService == null) {
            return new StoreCouponPlan(List.of(), java.math.BigDecimal.ZERO, context.originalAmount());
        }
        return couponRecommendationService.recommend(context, coupons);
    }

    private StoreCouponContext couponContext(List<StoreCartItem> cartItems, Map<Long, StoreSku> lockedSkus) {
        List<StoreCouponLine> lines = cartItems.stream().map(item -> {
            StoreSku sku = lockedSkus.getOrDefault(item.getSku().getId(), item.getSku());
            return new StoreCouponLine(sku.getProduct().getId(), sku.getProduct().getCategory(),
                    sku.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
        }).toList();
        return new StoreCouponContext(lines, lines.stream().map(StoreCouponLine::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }

    private void validateCoupons(List<StoreUserCoupon> coupons, List<Long> requestedIds) {
        if (requestedIds != null && !requestedIds.isEmpty()
                && (coupons.size() != new LinkedHashSet<>(requestedIds).size()
                || coupons.stream().anyMatch(c -> !couponUsable(c)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "优惠券不可用或不属于当前用户");
        }
    }

    private boolean couponUsable(StoreUserCoupon userCoupon) {
        StoreCoupon coupon = userCoupon.getCoupon();
        Instant now = Instant.now();
        return StoreUserCoupon.UNUSED.equals(userCoupon.getStatus()) && Boolean.TRUE.equals(coupon.getEnabled())
                && !now.isBefore(coupon.getValidFrom()) && !now.isAfter(coupon.getValidTo());
    }

    public StoreApiModels.OrderResponse find(Long orderId) {
        StoreOrder order = orderRepository.findByIdAndUserId(orderId, currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return StoreApiModels.OrderResponse.from(order);
    }

    public List<StoreApiModels.OrderResponse> findOrders(String status) {
        String normalizedStatus = normalizeStatus(status);
        List<StoreOrder> orders = normalizedStatus == null
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.userId())
                : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                        currentUser.userId(), normalizedStatus);
        return orders.stream().map(StoreApiModels.OrderResponse::from).toList();
    }

    @Transactional
    public StoreApiModels.OrderResponse pay(Long orderId) {
        StoreOrder order = lockedOrder(orderId);
        if (StoreOrder.STATUS_PAID.equals(order.getStatus())) {
            return StoreApiModels.OrderResponse.from(order);
        }
        if (!StoreOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已取消订单不能支付");
        }
        order.pay(nextPaymentNo());
        return StoreApiModels.OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public StoreApiModels.OrderResponse cancel(Long orderId) {
        StoreOrder order = lockedOrder(orderId);
        if (StoreOrder.STATUS_CANCELLED.equals(order.getStatus())) {
            return StoreApiModels.OrderResponse.from(order);
        }
        if (!StoreOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已支付订单不能取消");
        }

        List<Long> skuIds = order.getItems().stream().map(StoreOrderItem::getSkuId)
                .distinct().sorted().toList();
        Map<Long, StoreSku> lockedSkus = new HashMap<>();
        skuRepository.findAllByIdForUpdate(skuIds)
                .forEach(sku -> lockedSkus.put(sku.getId(), sku));
        if (lockedSkus.size() != skuIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单中的部分SKU已不存在，无法回补库存");
        }
        order.getItems().forEach(item -> lockedSkus.get(item.getSkuId()).restoreStock(item.getQuantity()));
        order.cancel();
        return StoreApiModels.OrderResponse.from(orderRepository.save(order));
    }

    private StoreOrder lockedOrder(Long orderId) {
        return orderRepository.findOwnedByIdForUpdate(orderId, currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of(StoreOrder.STATUS_CREATED, StoreOrder.STATUS_PAID, StoreOrder.STATUS_CANCELLED)
                .contains(normalized)) {
            throw badRequest("不支持的订单状态");
        }
        return normalized;
    }

    private void validate(StoreApiModels.CreateOrderRequest request) {
        if (request == null || request.cartItemIds() == null || request.cartItemIds().isEmpty()) {
            throw badRequest("请选择要结算的购物车商品");
        }
        if (request.addressId() == null) {
            throw badRequest("请选择收货地址");
        }
    }

    private void validate(StoreApiModels.CheckoutPreviewRequest request) {
        if (request == null || request.cartItemIds() == null || request.cartItemIds().isEmpty()) {
            throw badRequest("请选择要结算的购物车商品");
        }
        if (request.addressId() == null) {
            throw badRequest("请选择收货地址");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String nextOrderNo() {
        return "MO" + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
