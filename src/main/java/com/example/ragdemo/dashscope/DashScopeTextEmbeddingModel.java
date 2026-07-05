package com.example.ragdemo.dashscope;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.utils.Constants;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.StringUtils;

public class DashScopeTextEmbeddingModel implements EmbeddingModel {

    private final String apiKey;

    private final String model;

    private final Integer dimensions;

    private final String baseUrl;

    public DashScopeTextEmbeddingModel(String apiKey, String model, Integer dimensions, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.baseUrl = baseUrl;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            List<String> texts = request.getInstructions();
            TextEmbeddingParam.TextEmbeddingParamBuilder<?, ?> builder = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .texts(texts);
            Integer requestDimensions = request.getOptions() == null ? null : request.getOptions().getDimensions();
            Integer effectiveDimensions = requestDimensions == null ? dimensions : requestDimensions;
            if (effectiveDimensions != null && effectiveDimensions > 0) {
                builder.dimension(effectiveDimensions);
            }
            TextEmbeddingResult result = callDashScope(builder.build());
            List<Embedding> embeddings = result.getOutput().getEmbeddings().stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .map(item -> new Embedding(toFloatArray(item.getEmbedding()), item.getTextIndex()))
                    .collect(Collectors.toList());
            return new EmbeddingResponse(embeddings);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DashScope embedding call failed for model " + model, ex);
        }
    }

    @Override
    public float[] embed(Document document) {
        return call(new EmbeddingRequest(List.of(getEmbeddingContent(document)), null)).getResult().getOutput();
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return Objects.toString(document.getText(), "");
    }

    @Override
    public int dimensions() {
        return dimensions == null ? EmbeddingModel.super.dimensions() : dimensions;
    }

    private TextEmbeddingResult callDashScope(TextEmbeddingParam param) throws Exception {
        if (!StringUtils.hasText(baseUrl)) {
            return new TextEmbedding().call(param);
        }
        synchronized (DashScopeTextEmbeddingModel.class) {
            String previousBaseUrl = Constants.baseHttpApiUrl;
            try {
                Constants.baseHttpApiUrl = baseUrl;
                return new TextEmbedding().call(param);
            } finally {
                Constants.baseHttpApiUrl = previousBaseUrl;
            }
        }
    }

    private float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }
}
