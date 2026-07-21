package com.example.ragdemo.store;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StoreCartService {

    private final StoreCartItemRepository cartRepository;
    private final StoreSkuRepository skuRepository;
    private final CurrentStoreUser currentUser;

    public StoreCartService(StoreCartItemRepository cartRepository, StoreSkuRepository skuRepository,
            CurrentStoreUser currentUser) {
        this.cartRepository = cartRepository;
        this.skuRepository = skuRepository;
        this.currentUser = currentUser;
    }

    public StoreApiModels.CartResponse findCart() {
        return toResponse(cartRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.userId()));
    }

    @Transactional
    public StoreApiModels.CartResponse add(StoreApiModels.AddCartItemRequest request) {
        if (request == null || request.skuId() == null) {
            throw badRequest("请选择SKU");
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        requirePositive(quantity);
        StoreSku sku = skuRepository.findByIdAndEnabledTrue(request.skuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU不存在或已下架"));
        StoreCartItem item = cartRepository.findByUserIdAndSkuId(currentUser.userId(), sku.getId())
                .orElseGet(() -> new StoreCartItem(currentUser.userId(), sku, 0));
        int nextQuantity = item.getQuantity() + quantity;
        requireStock(sku, nextQuantity);
        item.setQuantity(nextQuantity);
        cartRepository.save(item);
        return findCart();
    }

    @Transactional
    public StoreApiModels.CartResponse update(Long itemId, StoreApiModels.UpdateCartItemRequest request) {
        if (request == null || request.quantity() == null) {
            throw badRequest("购物车数量不能为空");
        }
        requirePositive(request.quantity());
        StoreCartItem item = ownedItem(itemId);
        if (!Boolean.TRUE.equals(item.getSku().getEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU已下架");
        }
        requireStock(item.getSku(), request.quantity());
        item.setQuantity(request.quantity());
        return findCart();
    }

    @Transactional
    public StoreApiModels.CartResponse delete(Long itemId) {
        cartRepository.delete(ownedItem(itemId));
        return findCart();
    }

    private StoreCartItem ownedItem(Long itemId) {
        return cartRepository.findByIdAndUserId(itemId, currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "购物车项不存在"));
    }

    private StoreApiModels.CartResponse toResponse(List<StoreCartItem> items) {
        List<StoreApiModels.CartItemResponse> responses = items.stream()
                .map(StoreApiModels.CartItemResponse::from).toList();
        int totalQuantity = responses.stream().mapToInt(StoreApiModels.CartItemResponse::quantity).sum();
        BigDecimal totalAmount = responses.stream().map(StoreApiModels.CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StoreApiModels.CartResponse(responses, totalQuantity, totalAmount);
    }

    private void requirePositive(int quantity) {
        if (quantity < 1) {
            throw badRequest("购物车数量必须大于0");
        }
    }

    private void requireStock(StoreSku sku, int quantity) {
        if (quantity > sku.getStock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "库存不足，当前最多可购买" + sku.getStock() + "件");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
