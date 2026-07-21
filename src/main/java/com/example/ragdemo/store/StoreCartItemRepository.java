package com.example.ragdemo.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCartItemRepository extends JpaRepository<StoreCartItem, Long> {
    @EntityGraph(attributePaths = {"sku", "sku.product"})
    List<StoreCartItem> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"sku", "sku.product"})
    Optional<StoreCartItem> findByUserIdAndSkuId(Long userId, Long skuId);

    @EntityGraph(attributePaths = {"sku", "sku.product"})
    Optional<StoreCartItem> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"sku", "sku.product"})
    List<StoreCartItem> findByIdInAndUserId(Collection<Long> ids, Long userId);
}
