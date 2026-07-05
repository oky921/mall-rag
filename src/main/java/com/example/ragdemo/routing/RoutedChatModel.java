package com.example.ragdemo.routing;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

public class RoutedChatModel implements ChatModel {

    private final List<ModelEndpoint<ChatModel>> endpoints;

    private final ModelRouter modelRouter;

    public RoutedChatModel(List<ModelEndpoint<ChatModel>> endpoints, ModelRouter modelRouter) {
        this.endpoints = List.copyOf(endpoints);
        this.modelRouter = modelRouter;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return modelRouter.execute(ModelCapability.CHAT, endpoints, endpoint -> endpoint.getDelegate().call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return firstEndpoint().getDefaultOptions();
    }

    @Override
    public ChatOptions getOptions() {
        return firstEndpoint().getOptions();
    }

    private ChatModel firstEndpoint() {
        return endpoints.stream()
                .filter(ModelEndpoint::isEnabled)
                .sorted(java.util.Comparator.comparingInt(ModelEndpoint::getPriority))
                .findFirst()
                .map(ModelEndpoint::getDelegate)
                .orElseThrow(() -> new IllegalStateException("No enabled chat model configured"));
    }
}
