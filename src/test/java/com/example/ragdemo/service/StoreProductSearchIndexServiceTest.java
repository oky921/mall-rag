package com.example.ragdemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StoreProductSearchIndexServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsProductToStableVectorDocumentWithoutStock() {
        StoreProduct product = mock(StoreProduct.class);
        when(product.getId()).thenReturn(2L);
        when(product.getCode()).thenReturn("DIG-1002");
        when(product.getName()).thenReturn("静界降噪耳机");
        when(product.getSubtitle()).thenReturn("沉浸声场");
        when(product.getDescription()).thenReturn("适合通勤");
        when(product.getCategory()).thenReturn("数码家电");
        when(product.getPrice()).thenReturn(new BigDecimal("899"));
        when(product.getImageUrl()).thenReturn("https://example.com/headphones.jpg");

        ObjectProvider<RagService> ragServiceProvider = mock(ObjectProvider.class);
        StoreProductSearchIndexService service = new StoreProductSearchIndexService(
                mock(StoreProductRepository.class), ragServiceProvider);
        RagDocumentRequest document = service.toDocument(product);

        assertThat(document.getId()).isEqualTo("store-product:DIG-1002");
        assertThat(document.getProductId()).isEqualTo("DIG-1002");
        assertThat(document.getLink()).isEqualTo("/mall/products/2");
        assertThat(document.getMetadata()).containsEntry("mysql_product_id", 2L);
        assertThat(document.getMetadata()).containsKey("index_version");
        assertThat(document.getMetadata()).doesNotContainKey("stock");
        assertThat(document.getMetadata()).doesNotContainKey("sales");
        verifyNoInteractions(ragServiceProvider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildRemainsCompatibleAndUsesStableDocuments() {
        StoreProductRepository repository = mock(StoreProductRepository.class);
        ObjectProvider<RagService> ragServiceProvider = mock(ObjectProvider.class);
        RagService ragService = mock(RagService.class);
        StoreProduct product = product("P-1", 1L, "Name");
        when(repository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(product));
        when(ragServiceProvider.getObject()).thenReturn(ragService);
        when(ragService.replaceAll(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(com.example.ragdemo.dto.RagIngestResponse.ok(1));
        StoreProductSearchIndexService service = new StoreProductSearchIndexService(repository, ragServiceProvider);

        assertThat(service.rebuild().getDocuments()).isEqualTo(1);

        @SuppressWarnings("rawtypes")
        org.mockito.ArgumentCaptor<List> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(ragService).replaceAll(captor.capture());
        RagDocumentRequest request = (RagDocumentRequest) captor.getValue().getFirst();
        assertThat(request.getId()).isEqualTo("store-product:P-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliationAddsUpdatesAndDeletesToMatchMysql() {
        StoreProductRepository repository = mock(StoreProductRepository.class);
        ObjectProvider<RagService> ragServiceProvider = mock(ObjectProvider.class);
        ObjectProvider<StoreProductIndexInventory> inventoryProvider = mock(ObjectProvider.class);
        RagService ragService = mock(RagService.class);
        StoreProductIndexInventory inventory = mock(StoreProductIndexInventory.class);
        StoreProduct current = product("CURRENT", 1L, "Current");
        StoreProduct missing = product("MISSING", 2L, "Missing");
        when(repository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(current, missing));
        when(ragServiceProvider.getObject()).thenReturn(ragService);
        when(inventoryProvider.getIfAvailable()).thenReturn(inventory);
        when(inventory.listProductVersions()).thenReturn(Map.of(
                "store-product:CURRENT", "old-version",
                "store-product:ORPHAN", "orphan-version"));
        StoreProductSearchIndexService service = new StoreProductSearchIndexService(
                repository, ragServiceProvider, inventoryProvider);

        var response = service.reconcile();

        assertThat(response.added()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.deleted()).isEqualTo(1);
        verify(ragService, org.mockito.Mockito.times(2)).replaceAll(org.mockito.ArgumentMatchers.anyList());
        verify(ragService).deleteDocuments(List.of("store-product:ORPHAN"));
    }

    private StoreProduct product(String code, long id, String name) {
        StoreProduct product = mock(StoreProduct.class);
        when(product.getId()).thenReturn(id);
        when(product.getCode()).thenReturn(code);
        when(product.getName()).thenReturn(name);
        when(product.getSubtitle()).thenReturn("Subtitle");
        when(product.getDescription()).thenReturn("Description");
        when(product.getCategory()).thenReturn("Category");
        when(product.getPrice()).thenReturn(new BigDecimal("99.00"));
        when(product.getImageUrl()).thenReturn("https://example.com/product.jpg");
        return product;
    }
}
