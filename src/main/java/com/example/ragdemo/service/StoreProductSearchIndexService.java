package com.example.ragdemo.service;

import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.dto.RagIngestResponse;
import com.example.ragdemo.dto.StoreProductIndexReconciliationResponse;
import com.example.ragdemo.exception.AiServiceException;
import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductRepository;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StoreProductSearchIndexService {

    private final StoreProductRepository productRepository;
    private final ObjectProvider<RagService> ragServiceProvider;
    private final ObjectProvider<StoreProductIndexInventory> inventoryProvider;

    public StoreProductSearchIndexService(StoreProductRepository productRepository,
            ObjectProvider<RagService> ragServiceProvider) {
        this(productRepository, ragServiceProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StoreProductSearchIndexService(StoreProductRepository productRepository,
            ObjectProvider<RagService> ragServiceProvider,
            ObjectProvider<StoreProductIndexInventory> inventoryProvider) {
        this.productRepository = productRepository;
        this.ragServiceProvider = ragServiceProvider;
        this.inventoryProvider = inventoryProvider;
    }

    public RagIngestResponse rebuild() {
        List<RagDocumentRequest> documents = productRepository.findAllByActiveTrueOrderByIdAsc().stream()
                .map(this::toDocument)
                .toList();
        try {
            return ragServiceProvider.getObject().replaceAll(documents);
        } catch (BeansException ex) {
            throw new AiServiceException(
                    "Product search index is unavailable because Milvus could not be initialized.", ex);
        }
    }

    public void upsert(StoreProduct product) {
        ragService().replaceAll(List.of(toDocument(product)));
    }

    public void delete(String productCode) {
        ragService().deleteDocuments(List.of(documentId(productCode)));
    }

    public StoreProductIndexReconciliationResponse reconcile() {
        Map<String, StoreProduct> expected = new LinkedHashMap<>();
        for (StoreProduct product : productRepository.findAllByActiveTrueOrderByIdAsc()) {
            expected.put(documentId(product.getCode()), product);
        }
        StoreProductIndexInventory inventory = inventoryProvider == null ? null : inventoryProvider.getIfAvailable();
        if (inventory == null) {
            throw new AiServiceException("Milvus product inventory is unavailable.", null);
        }
        Map<String, String> actual = inventory.listProductVersions();
        int added = 0;
        int updated = 0;
        for (Map.Entry<String, StoreProduct> entry : expected.entrySet()) {
            String actualVersion = actual.get(entry.getKey());
            if (actualVersion == null) {
                upsert(entry.getValue());
                added++;
            } else if (!indexVersion(entry.getValue()).equals(actualVersion)) {
                upsert(entry.getValue());
                updated++;
            }
        }
        int deleted = 0;
        for (String documentId : actual.keySet()) {
            if (!expected.containsKey(documentId)) {
                ragService().deleteDocuments(List.of(documentId));
                deleted++;
            }
        }
        return new StoreProductIndexReconciliationResponse(expected.size(), actual.size(), added, updated, deleted);
    }

    private RagService ragService() {
        try {
            return ragServiceProvider.getObject();
        } catch (BeansException ex) {
            throw new AiServiceException(
                    "Product search index is unavailable because Milvus could not be initialized.", ex);
        }
    }

    RagDocumentRequest toDocument(StoreProduct product) {
        RagDocumentRequest request = new RagDocumentRequest();
        request.setId("store-product:" + product.getCode());
        request.setSource("mysql-store");
        request.setType("product");
        request.setTitle(product.getName());
        request.setProductId(product.getCode());
        request.setLink("/mall/products/" + product.getId());
        request.setContent("%s。%s。%s。商品分类：%s。适用需求和特点以商品描述为准。"
                .formatted(product.getName(), product.getSubtitle(), product.getDescription(), product.getCategory()));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("mysql_product_id", product.getId());
        metadata.put("category", product.getCategory());
        metadata.put("price", product.getPrice());
        metadata.put("image_url", product.getImageUrl());
        metadata.put("index_version", indexVersion(product));
        request.setMetadata(metadata);
        return request;
    }

    String indexVersion(StoreProduct product) {
        String indexedState = String.join("\u001f",
                value(product.getCode()), value(product.getId()), value(product.getName()),
                value(product.getSubtitle()), value(product.getDescription()), value(product.getCategory()),
                value(product.getPrice()), value(product.getImageUrl()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(indexedState.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String documentId(String productCode) {
        return "store-product:" + productCode;
    }
}
