package com.example.ragdemo.service;

import com.example.ragdemo.dashscope.DashScopeMultiModalEmbeddingModel;
import com.example.ragdemo.dto.MallImageDocumentRequest;
import com.example.ragdemo.dto.MallImageIngestResponse;
import com.example.ragdemo.dto.MallImageSearchRequest;
import com.example.ragdemo.dto.MallImageSearchResponse;
import com.example.ragdemo.dto.MallImageSearchResult;
import com.example.ragdemo.exception.AiConfigurationException;
import com.example.ragdemo.exception.AiServiceException;
import com.example.ragdemo.exception.BadRequestException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.image-rag.enabled", havingValue = "true")
public class ImageRagService {

    private static final int DEFAULT_TOP_K = 8;

    private static final int MAX_TOP_K = 30;

    private static final String ID_FIELD = "id";

    private static final String VECTOR_FIELD = "embedding";

    private final DashScopeMultiModalEmbeddingModel embeddingModel;

    private final MilvusClientV2 milvusClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String database;

    private final String collection;

    private volatile boolean collectionReady;

    public ImageRagService(DashScopeMultiModalEmbeddingModel embeddingModel,
            @Qualifier("imageRagMilvusClient") MilvusClientV2 imageRagMilvusClient,
            @Value("${app.image-rag.milvus.database:${spring.ai.vectorstore.milvus.database-name:default}}") String database,
            @Value("${app.image-rag.milvus.collection:mall_product_images}") String collection) {
        this.embeddingModel = embeddingModel;
        this.milvusClient = imageRagMilvusClient;
        this.database = database;
        this.collection = collection;
    }

    public MallImageIngestResponse ingest(MallImageDocumentRequest request) {
        return ingestAll(List.of(request));
    }

    public MallImageIngestResponse ingestAll(List<MallImageDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("images cannot be empty");
        }

        List<JsonObject> rows = new ArrayList<>();
        try {
            for (MallImageDocumentRequest request : requests) {
                String imageUrl = normalizeImageUrl(request);
                float[] vector = embeddingModel.embedImage(imageUrl);
                ensureCollection(vector.length);
                rows.add(toMilvusRow(request, imageUrl, vector));
            }
            milvusClient.insert(InsertReq.builder()
                    .collectionName(collection)
                    .data(rows)
                    .build());
            return MallImageIngestResponse.ok(rows.size());
        } catch (BadRequestException | AiConfigurationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiServiceException("Failed to write image vectors to Milvus.", ex);
        }
    }

    public MallImageSearchResponse search(MallImageSearchRequest request) {
        float[] vector = buildQueryVector(request);
        ensureCollection(vector.length);
        int topK = normalizeTopK(request);

        try {
            SearchResp response = milvusClient.search(SearchReq.builder()
                    .databaseName(database)
                    .collectionName(collection)
                    .annsField(VECTOR_FIELD)
                    .metricType(IndexParam.MetricType.COSINE)
                    .data(List.of(new FloatVec(vector)))
                    .topK(topK)
                    .filter(normalizeFilter(request))
                    .outputFields(List.of(ID_FIELD, "product_id", "sku_id", "title", "image_url", "link", "source",
                            "metadata"))
                    .build());
            return MallImageSearchResponse.ok(toSearchResults(response));
        } catch (RuntimeException ex) {
            throw new AiServiceException("Failed to search image vectors in Milvus.", ex);
        }
    }

    public float[] embedImage(String image) {
        return embeddingModel.embedImage(image);
    }

    private float[] buildQueryVector(MallImageSearchRequest request) {
        if (request == null) {
            throw new BadRequestException("search request cannot be empty");
        }
        if (StringUtils.hasText(request.getImageUrl())) {
            return embeddingModel.embedImage(request.getImageUrl().trim());
        }
        if (StringUtils.hasText(request.getQuery())) {
            return embeddingModel.embedText(request.getQuery().trim());
        }
        throw new BadRequestException("query or image_url cannot be empty");
    }

    private JsonObject toMilvusRow(MallImageDocumentRequest request, String imageUrl, float[] vector) {
        JsonObject row = new JsonObject();
        row.addProperty(ID_FIELD, normalizeId(request, imageUrl));
        row.addProperty("product_id", normalizeText(request.getProductId(), ""));
        row.addProperty("sku_id", normalizeText(request.getSkuId(), ""));
        row.addProperty("title", normalizeText(request.getTitle(), ""));
        row.addProperty("image_url", imageUrl);
        row.addProperty("link", normalizeText(request.getLink(), ""));
        row.addProperty("source", normalizeText(request.getSource(), "product-image"));
        row.add(VECTOR_FIELD, toJsonArray(vector));
        row.add("metadata", toJsonObject(request.getMetadata()));
        return row;
    }

    private void ensureCollection(int dimension) {
        if (collectionReady) {
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                return;
            }
            try {
                Boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                        .collectionName(collection)
                        .build());
                if (!Boolean.TRUE.equals(exists)) {
                    createCollection(dimension);
                }
                milvusClient.loadCollection(LoadCollectionReq.builder()
                        .collectionName(collection)
                        .sync(true)
                        .build());
                collectionReady = true;
            } catch (RuntimeException ex) {
                throw new AiServiceException("Failed to initialize Milvus image collection.", ex);
            }
        }
    }

    private void createCollection(int dimension) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .fieldSchemaList(List.of(
                        varcharField(ID_FIELD, 128, true),
                        varcharField("product_id", 128, false),
                        varcharField("sku_id", 128, false),
                        varcharField("title", 512, false),
                        varcharField("image_url", 2048, false),
                        varcharField("link", 2048, false),
                        varcharField("source", 256, false),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("metadata")
                                .dataType(DataType.JSON)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(VECTOR_FIELD)
                                .dataType(DataType.FloatVector)
                                .dimension(dimension)
                                .build()))
                .build();

        IndexParam indexParam = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexName("idx_" + VECTOR_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        milvusClient.createCollection(CreateCollectionReq.builder()
                .databaseName(database)
                .collectionName(collection)
                .description("Mall product image vectors")
                .collectionSchema(schema)
                .indexParams(List.of(indexParam))
                .build());
    }

    private CreateCollectionReq.FieldSchema varcharField(String name, int maxLength, boolean primaryKey) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .isPrimaryKey(primaryKey)
                .autoID(false)
                .build();
    }

    private List<MallImageSearchResult> toSearchResults(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }

        return response.getSearchResults().get(0).stream()
                .map(this::toSearchResult)
                .toList();
    }

    private MallImageSearchResult toSearchResult(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity() == null ? Map.of() : result.getEntity();
        return new MallImageSearchResult(
                objectToString(entity.getOrDefault(ID_FIELD, result.getId())),
                objectToString(entity.get("product_id")),
                objectToString(entity.get("sku_id")),
                objectToString(entity.get("title")),
                objectToString(entity.get("image_url")),
                objectToString(entity.get("link")),
                result.getScore(),
                normalizeMetadata(entity.get("metadata")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMetadata(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> metadata = new HashMap<>();
            raw.forEach((key, mapValue) -> {
                if (key != null && mapValue != null) {
                    metadata.put(key.toString(), mapValue);
                }
            });
            return metadata;
        }
        return Map.of();
    }

    private String normalizeImageUrl(MallImageDocumentRequest request) {
        if (request == null || !StringUtils.hasText(request.getImageUrl())) {
            throw new BadRequestException("image_url cannot be empty");
        }
        return request.getImageUrl().trim();
    }

    private String normalizeId(MallImageDocumentRequest request, String imageUrl) {
        if (StringUtils.hasText(request.getId())) {
            return request.getId().trim();
        }
        if (StringUtils.hasText(request.getProductId()) && StringUtils.hasText(request.getSkuId())) {
            return request.getProductId().trim() + ":" + request.getSkuId().trim();
        }
        if (StringUtils.hasText(request.getProductId())) {
            return request.getProductId().trim() + ":" + sha256(imageUrl).substring(0, 24);
        }
        return "image:" + sha256(imageUrl).substring(0, 32);
    }

    private int normalizeTopK(MallImageSearchRequest request) {
        Integer topK = request == null ? null : request.getTopK();
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new BadRequestException("topK must be between 1 and " + MAX_TOP_K);
        }
        return topK;
    }

    private String normalizeFilter(MallImageSearchRequest request) {
        return request != null && StringUtils.hasText(request.getFilter()) ? request.getFilter().trim() : null;
    }

    private String normalizeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String objectToString(Object value) {
        return value == null ? null : value.toString();
    }

    private JsonArray toJsonArray(float[] vector) {
        JsonArray array = new JsonArray(vector.length);
        for (float value : vector) {
            array.add(value);
        }
        return array;
    }

    private JsonObject toJsonObject(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(objectMapper.writeValueAsString(metadata)).getAsJsonObject();
        } catch (JacksonException ex) {
            throw new BadRequestException("metadata must be JSON serializable");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
