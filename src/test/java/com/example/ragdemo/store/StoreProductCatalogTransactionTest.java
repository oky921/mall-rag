package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(StoreProductCatalogService.class)
class StoreProductCatalogTransactionTest {

    @Autowired
    private StoreProductCatalogService service;

    @Autowired
    private StoreProductRepository productRepository;

    @Autowired
    private StoreProductIndexOutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void productAndOutboxCommitTogether() {
        transactionTemplate().executeWithoutResult(status -> service.save(product("TX-1")));

        assertThat(productRepository.findByCode("TX-1")).isPresent();
        assertThat(outboxRepository.findByProductCodeOrderByCreatedAtAsc("TX-1"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(StoreProductIndexEventType.UPSERT);
                    assertThat(event.getStatus()).isEqualTo(StoreProductIndexEventStatus.PENDING);
                });
    }

    @Test
    void productAndOutboxRollBackTogether() {
        try {
            transactionTemplate().executeWithoutResult(status -> {
                service.save(product("TX-ROLLBACK"));
                throw new IllegalStateException("rollback");
            });
        } catch (IllegalStateException expected) {
            // Expected test rollback.
        }

        assertThat(productRepository.findByCode("TX-ROLLBACK")).isEmpty();
        assertThat(outboxRepository.findByProductCodeOrderByCreatedAtAsc("TX-ROLLBACK")).isEmpty();
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private StoreProduct product(String code) {
        return new StoreProduct(code, "Name", "Subtitle", "Description", "Category",
                new BigDecimal("99.00"), new BigDecimal("109.00"), 10, 20,
                new BigDecimal("4.5"), "https://example.com/product.jpg", true);
    }
}
