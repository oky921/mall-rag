package com.example.ragdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
public class MilvusStoreProductIndexInventory implements StoreProductIndexInventory {

    private static final int PAGE_SIZE = 1000;
    private static final String DOCUMENT_ID_FIELD = "doc_id";
    private static final String METADATA_FIELD = "metadata";

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectMapper objectMapper;
    private final String databaseName;
    private final String collectionName;

    public MilvusStoreProductIndexInventory(ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
            @Value("${spring.ai.vectorstore.milvus.collection-name:vector_store}") String collectionName) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.objectMapper = objectMapper;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @Override
    public Map<String, String> listProductVersions() {
        VectorStore vectorStore = vectorStoreProvider.getObject();
        MilvusServiceClient client = vectorStore.<MilvusServiceClient>getNativeClient()
                .orElseThrow(() -> new IllegalStateException("Milvus native client is unavailable"));
        Map<String, String> versions = new LinkedHashMap<>();
        long offset = 0;
        while (true) {
            QueryParam query = QueryParam.newBuilder()
                    .withDatabaseName(databaseName)
                    .withCollectionName(collectionName)
                    .withExpr("metadata[\"type\"] == \"product\"")
                    .withOutFields(List.of(DOCUMENT_ID_FIELD, METADATA_FIELD))
                    .withOffset(offset)
                    .withLimit((long) PAGE_SIZE)
                    .build();
            R<QueryResults> response = client.query(query);
            if (response.getStatus() != 0) {
                throw new IllegalStateException("Milvus product inventory query failed: " + response.getMessage());
            }
            List<QueryResultsWrapper.RowRecord> rows = new QueryResultsWrapper(response.getData()).getRowRecords();
            for (QueryResultsWrapper.RowRecord row : rows) {
                Object id = row.get(DOCUMENT_ID_FIELD);
                Map<String, Object> metadata = metadata(row.get(METADATA_FIELD));
                if (id != null) {
                    Object version = metadata.get("index_version");
                    versions.put(id.toString(), version == null ? "" : version.toString());
                }
            }
            if (rows.size() < PAGE_SIZE) {
                return versions;
            }
            offset += rows.size();
        }
    }

    private Map<String, Object> metadata(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse Milvus product metadata", ex);
        }
    }
}
