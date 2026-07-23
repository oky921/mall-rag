package com.example.ragdemo.store;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StoreUserCouponRepository extends JpaRepository<StoreUserCoupon, Long> {

    @EntityGraph(attributePaths = "coupon")
    List<StoreUserCoupon> findByUserIdAndStatus(Long userId, String status);

    @EntityGraph(attributePaths = "coupon")
    List<StoreUserCoupon> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserIdAndCouponId(Long userId, Long couponId);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "coupon")
    @Query("select uc from StoreUserCoupon uc where uc.userId = :userId and uc.id in :ids")
    List<StoreUserCoupon> findOwnedByIdsForUpdate(@Param("userId") Long userId,
            @Param("ids") Collection<Long> ids);
}
