package com.example.ragdemo.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, Long> {
    List<StoreAddress> findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(Long userId);
    Optional<StoreAddress> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserId(Long userId);
}
