package com.example.ragdemo.store;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreSkuRepository extends JpaRepository<StoreSku, Long> {
    List<StoreSku> findByProductIdAndEnabledTrueOrderById(Long productId);

    @EntityGraph(attributePaths = "product")
    Optional<StoreSku> findByIdAndEnabledTrue(Long id);

    boolean existsByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "product")
    @Query("select sku from StoreSku sku where sku.id in :ids order by sku.id")
    List<StoreSku> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
