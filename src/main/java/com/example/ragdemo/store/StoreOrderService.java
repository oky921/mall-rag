package com.example.ragdemo.store;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public StoreOrderService(StoreOrderRepository orderRepository, StoreCartItemRepository cartRepository,
            StoreSkuRepository skuRepository, CurrentStoreUser currentUser,
            StoreAddressService addressService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.skuRepository = skuRepository;
        this.currentUser = currentUser;
        this.addressService = addressService;
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

        StoreOrder saved = orderRepository.save(order);
        cartRepository.deleteAll(cartItems);
        return StoreApiModels.OrderResponse.from(saved);
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
