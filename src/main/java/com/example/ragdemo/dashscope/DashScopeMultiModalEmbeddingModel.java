package com.example.ragdemo.dashscope;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemImage;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class DashScopeMultiModalEmbeddingModel {

    private final String apiKey;

    private final String model;

    public DashScopeMultiModalEmbeddingModel(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public float[] embedText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text cannot be empty");
        }
        return embed(new MultiModalEmbeddingItemText(text.trim()));
    }

    public float[] embedImage(String image) {
        if (!StringUtils.hasText(image)) {
            throw new IllegalArgumentException("image cannot be empty");
        }
        return embed(new MultiModalEmbeddingItemImage(image.trim()));
    }

    private float[] embed(MultiModalEmbeddingItemBase content) {
        try {
            MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .contents(List.of(content))
                    .build();
            MultiModalEmbeddingResult result = new MultiModalEmbedding().call(param);
            return toFloatArray(extractEmbedding(result));
        } catch (UploadFileException | NoApiKeyException ex) {
            throw new IllegalStateException("DashScope multimodal embedding failed for model " + model, ex);
        }
    }

    private List<Double> extractEmbedding(MultiModalEmbeddingResult result) {
        if (result == null || result.getOutput() == null) {
            throw new IllegalStateException("DashScope multimodal embedding result is empty for model " + model);
        }
        if (result.getOutput().getEmbeddings() != null && !result.getOutput().getEmbeddings().isEmpty()) {
            return result.getOutput().getEmbeddings().stream()
                    .filter(Objects::nonNull)
                    .map(MultiModalEmbeddingResultItem::getEmbedding)
                    .filter(values -> values != null && !values.isEmpty())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "DashScope multimodal embedding output has no vector for model " + model));
        }
        if (result.getOutput().getEmbedding() != null && !result.getOutput().getEmbedding().isEmpty()) {
            return result.getOutput().getEmbedding();
        }
        throw new IllegalStateException("DashScope multimodal embedding output has no vector for model " + model);
    }

    private float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }
}
