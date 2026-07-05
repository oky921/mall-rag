package com.example.ragdemo.dashscope;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

public class DashScopeMultiModalChatModel implements ChatModel {

    private final String apiKey;

    private final String model;

    private final String baseUrl;

    public DashScopeMultiModalChatModel(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .messages(toDashScopeMessages(prompt))
                    .build();
            MultiModalConversationResult result = callDashScope(param);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(extractText(result)))));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DashScope chat call failed for model " + model, ex);
        }
    }

    private MultiModalConversationResult callDashScope(MultiModalConversationParam param) throws Exception {
        if (!StringUtils.hasText(baseUrl)) {
            return new MultiModalConversation().call(param);
        }
        synchronized (DashScopeMultiModalChatModel.class) {
            String previousBaseUrl = Constants.baseHttpApiUrl;
            try {
                Constants.baseHttpApiUrl = baseUrl;
                return new MultiModalConversation().call(param);
            } finally {
                Constants.baseHttpApiUrl = previousBaseUrl;
            }
        }
    }

    private List<MultiModalMessage> toDashScopeMessages(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            return List.of(toDashScopeMessage(Role.USER.getValue(), prompt.getContents()));
        }
        return messages.stream()
                .map(this::toDashScopeMessage)
                .collect(Collectors.toList());
    }

    private MultiModalMessage toDashScopeMessage(Message message) {
        String role = toDashScopeRole(message.getMessageType());
        return toDashScopeMessage(role, message.getText());
    }

    private MultiModalMessage toDashScopeMessage(String role, String text) {
        return MultiModalMessage.builder()
                .role(role)
                .content(List.of(Collections.singletonMap("text", Objects.toString(text, ""))))
                .build();
    }

    private String toDashScopeRole(MessageType messageType) {
        if (messageType == MessageType.SYSTEM) {
            return Role.SYSTEM.getValue();
        }
        if (messageType == MessageType.ASSISTANT) {
            return Role.ASSISTANT.getValue();
        }
        return Role.USER.getValue();
    }

    private String extractText(MultiModalConversationResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            throw new IllegalStateException("DashScope chat result is empty for model " + model);
        }
        MultiModalMessage message = result.getOutput().getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null) {
            throw new IllegalStateException("DashScope chat message is empty for model " + model);
        }
        return message.getContent().stream()
                .map(content -> content.get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining());
    }
}
