package com.example.ragdemo.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreCouponRepository extends JpaRepository<StoreCoupon, Long> {
    Optional<StoreCoupon> findByCode(String code);

    @Modifying
    @Query("update StoreCoupon c set c.stock = coalesce(c.stock, c.total, 1000) - 1, "
            + "c.issued = coalesce(c.issued, 0) + 1 where c.id = :id and coalesce(c.stock, c.total, 1000) > 0")
    int decrementStock(@Param("id") Long id);
}
