package com.example.ragdemo.store;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store")
public class StoreCommerceController {

    private final StoreCartService cartService;
    private final StoreOrderService orderService;

    public StoreCommerceController(StoreCartService cartService, StoreOrderService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @GetMapping("/cart")
    public StoreApiModels.CartResponse cart() {
        return cartService.findCart();
    }

    @PostMapping("/cart")
    public StoreApiModels.CartResponse addCartItem(@RequestBody StoreApiModels.AddCartItemRequest request) {
        return cartService.add(request);
    }

    @PutMapping("/cart/{itemId}")
    public StoreApiModels.CartResponse updateCartItem(@PathVariable Long itemId,
            @RequestBody StoreApiModels.UpdateCartItemRequest request) {
        return cartService.update(itemId, request);
    }

    @DeleteMapping("/cart/{itemId}")
    public StoreApiModels.CartResponse deleteCartItem(@PathVariable Long itemId) {
        return cartService.delete(itemId);
    }

    @PostMapping("/orders")
    public StoreApiModels.OrderResponse createOrder(@RequestBody StoreApiModels.CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/orders/{orderId}")
    public StoreApiModels.OrderResponse order(@PathVariable Long orderId) {
        return orderService.find(orderId);
    }

    @GetMapping("/orders")
    public java.util.List<StoreApiModels.OrderResponse> orders(
            @RequestParam(required = false) String status) {
        return orderService.findOrders(status);
    }

    @PostMapping("/orders/{orderId}/pay")
    public StoreApiModels.OrderResponse payOrder(@PathVariable Long orderId) {
        return orderService.pay(orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public StoreApiModels.OrderResponse cancelOrder(@PathVariable Long orderId) {
        return orderService.cancel(orderId);
    }
}
