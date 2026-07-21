package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreOrderServiceTest {

    @Mock
    private StoreOrderRepository orderRepository;

    @Mock
    private StoreCartItemRepository cartRepository;

    @Mock
    private StoreSkuRepository skuRepository;

    @Mock
    private CurrentStoreUser currentUser;

    @Mock
    private StoreAddressService addressService;

    @InjectMocks
    private StoreOrderService service;

    private StoreSku sku;
    private StoreCartItem cartItem;
    private StoreAddress address;

    @BeforeEach
    void setUp() {
        StoreProduct product = new StoreProduct("TEST-1", "测试商品", "副标题", "描述", "测试分类",
                new BigDecimal("100"), new BigDecimal("120"), 5, 0, new BigDecimal("4.8"),
                "https://example.com/product.jpg", true);
        ReflectionTestUtils.setField(product, "id", 1L);
        sku = new StoreSku(product, "TEST-1-BLACK", "{\"颜色\":\"黑色\"}",
                new BigDecimal("100"), new BigDecimal("120"), 5, 0,
                "https://example.com/product.jpg", true);
        ReflectionTestUtils.setField(sku, "id", 10L);
        cartItem = new StoreCartItem(1L, sku, 2);
        ReflectionTestUtils.setField(cartItem, "id", 20L);
        address = new StoreAddress(1L, new StoreApiModels.SaveAddressRequest(
                "演示用户", "13800000000", "上海市", "上海市", "浦东新区",
                "演示路1号", null, true), true);
        ReflectionTestUtils.setField(address, "id", 50L);
        when(currentUser.userId()).thenReturn(1L);
    }

    @Test
    void createsSnapshotDeductsStockAndClearsPurchasedCartItems() {
        when(cartRepository.findByIdInAndUserId(List.of(20L), 1L)).thenReturn(List.of(cartItem));
        when(addressService.ownedAddress(50L)).thenReturn(address);
        when(skuRepository.findAllByIdForUpdate(List.of(10L))).thenReturn(List.of(sku));
        when(orderRepository.save(any(StoreOrder.class))).thenAnswer(invocation -> {
            StoreOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 30L);
            return order;
        });

        StoreApiModels.OrderResponse response = service.create(new StoreApiModels.CreateOrderRequest(
                List.of(20L), 50L));

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.totalAmount()).isEqualByComparingTo("200");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("测试商品");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("200");
        });
        assertThat(sku.getStock()).isEqualTo(3);
        assertThat(sku.getSales()).isEqualTo(2);
        verify(cartRepository).deleteAll(List.of(cartItem));
    }

    @Test
    void doesNotCreateOrderWhenLockedSkuHasInsufficientStock() {
        sku.decreaseStock(4);
        when(addressService.ownedAddress(50L)).thenReturn(address);
        when(cartRepository.findByIdInAndUserId(List.of(20L), 1L)).thenReturn(List.of(cartItem));
        when(skuRepository.findAllByIdForUpdate(List.of(10L))).thenReturn(List.of(sku));

        assertThatThrownBy(() -> service.create(new StoreApiModels.CreateOrderRequest(
                List.of(20L), 50L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).deleteAll(any());
    }

    @Test
    void paysCreatedOrderAndReturnsSameResultWhenRetried() {
        StoreOrder order = orderWithItem();
        when(orderRepository.findOwnedByIdForUpdate(30L, 1L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        StoreApiModels.OrderResponse first = service.pay(30L);
        StoreApiModels.OrderResponse second = service.pay(30L);

        assertThat(first.status()).isEqualTo(StoreOrder.STATUS_PAID);
        assertThat(first.paymentNo()).startsWith("PAY");
        assertThat(first.paidAt()).isNotNull();
        assertThat(second.paymentNo()).isEqualTo(first.paymentNo());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void cancelsCreatedOrderAndRestoresStockOnlyOnce() {
        StoreOrder order = orderWithItem();
        sku.decreaseStock(2);
        when(orderRepository.findOwnedByIdForUpdate(30L, 1L)).thenReturn(java.util.Optional.of(order));
        when(skuRepository.findAllByIdForUpdate(List.of(10L))).thenReturn(List.of(sku));
        when(orderRepository.save(order)).thenReturn(order);

        StoreApiModels.OrderResponse first = service.cancel(30L);
        StoreApiModels.OrderResponse second = service.cancel(30L);

        assertThat(first.status()).isEqualTo(StoreOrder.STATUS_CANCELLED);
        assertThat(first.cancelledAt()).isNotNull();
        assertThat(second.cancelledAt()).isEqualTo(first.cancelledAt());
        assertThat(sku.getStock()).isEqualTo(5);
        assertThat(sku.getSales()).isZero();
        verify(skuRepository, times(1)).findAllByIdForUpdate(List.of(10L));
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void rejectsCancellingPaidOrderWithoutRestoringStock() {
        StoreOrder order = orderWithItem();
        order.pay("PAY-EXISTING");
        when(orderRepository.findOwnedByIdForUpdate(30L, 1L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> service.cancel(30L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(skuRepository, never()).findAllByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsPayingCancelledOrder() {
        StoreOrder order = orderWithItem();
        order.cancel();
        when(orderRepository.findOwnedByIdForUpdate(30L, 1L)).thenReturn(java.util.Optional.of(order));

        assertThatThrownBy(() -> service.pay(30L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(orderRepository, never()).save(any());
    }

    private StoreOrder orderWithItem() {
        StoreOrder order = new StoreOrder("MO-TEST", 1L, "演示用户", "13800000000", "上海市演示路1号");
        order.addItem(new StoreOrderItem(sku, 2));
        ReflectionTestUtils.setField(order, "id", 30L);
        return order;
    }
}
