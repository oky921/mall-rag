package com.example.ragdemo.store;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    @EntityGraph(attributePaths = "items")
    Optional<StoreOrder> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "items")
    List<StoreOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    List<StoreOrder> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("select storeOrder from StoreOrder storeOrder where storeOrder.id = :id and storeOrder.userId = :userId")
    Optional<StoreOrder> findOwnedByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
