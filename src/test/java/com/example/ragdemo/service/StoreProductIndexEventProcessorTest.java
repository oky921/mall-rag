package com.example.ragdemo.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductIndexEventType;
import com.example.ragdemo.store.StoreProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreProductIndexEventProcessorTest {

    @Mock StoreProductIndexEventClaimService claimService;
    @Mock StoreProductIndexEventStateService stateService;
    @Mock StoreProductRepository productRepository;
    @Mock StoreProductSearchIndexService indexService;
    @Mock StoreProduct product;

    private StoreProductIndexEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StoreProductIndexEventProcessor(
                claimService, stateService, productRepository, indexService);
    }

    @Test
    void upsertsCurrentActiveProductAndCompletes() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.of(product));
        when(product.getActive()).thenReturn(true);

        processor.process(event(1L, "P-1", StoreProductIndexEventType.UPSERT));

        verify(indexService).upsert(product);
        verify(stateService).complete(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletesWhenCurrentProductIsMissingEvenForOldUpsert() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.empty());

        processor.process(event(2L, "P-1", StoreProductIndexEventType.UPSERT));

        verify(indexService).delete("P-1");
        verify(indexService, never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void upsertsRecreatedProductEvenForOldDelete() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.of(product));
        when(product.getActive()).thenReturn(true);

        processor.process(event(3L, "P-1", StoreProductIndexEventType.DELETE));

        verify(indexService).upsert(product);
        verify(indexService, never()).delete("P-1");
    }

    @Test
    void deletesInactiveProduct() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.of(product));
        when(product.getActive()).thenReturn(false);

        processor.process(event(4L, "P-1", StoreProductIndexEventType.DELETE));

        verify(indexService).delete("P-1");
    }

    @Test
    void recordsFailureForRetryWhenMilvusIsUnavailable() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.of(product));
        when(product.getActive()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("milvus down")).when(indexService).upsert(product);

        processor.process(event(5L, "P-1", StoreProductIndexEventType.UPSERT));

        verify(stateService).fail(org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(IllegalStateException.class));
        verify(stateService, never()).complete(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedDeliveryUsesSameStableIdOperation() {
        when(productRepository.findByCode("P-1")).thenReturn(Optional.of(product));
        when(product.getActive()).thenReturn(true);
        StoreProductIndexEventClaimService.ClaimedEvent event = event(6L, "P-1", StoreProductIndexEventType.UPSERT);

        processor.process(event);
        processor.process(event);

        verify(indexService, times(2)).upsert(product);
    }

    private StoreProductIndexEventClaimService.ClaimedEvent event(
            long id, String code, StoreProductIndexEventType type) {
        return new StoreProductIndexEventClaimService.ClaimedEvent(id, code, type);
    }
}
