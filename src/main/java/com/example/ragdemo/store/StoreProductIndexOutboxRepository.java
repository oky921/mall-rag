package com.example.ragdemo.store;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProductIndexOutboxRepository
        extends JpaRepository<StoreProductIndexOutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from StoreProductIndexOutboxEvent event
            where (event.status = com.example.ragdemo.store.StoreProductIndexEventStatus.PENDING
                   and event.nextRetryAt <= :now)
               or (event.status = com.example.ragdemo.store.StoreProductIndexEventStatus.PROCESSING
                   and event.nextRetryAt <= :now)
            order by event.createdAt asc, event.id asc
            """)
    List<StoreProductIndexOutboxEvent> lockDue(@Param("now") Instant now, Pageable pageable);

    List<StoreProductIndexOutboxEvent> findByProductCodeOrderByCreatedAtAsc(String productCode);
}
