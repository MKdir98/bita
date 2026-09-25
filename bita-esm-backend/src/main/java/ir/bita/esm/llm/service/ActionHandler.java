package ir.bita.esm.llm.service;

import ir.bita.esm.llm.dto.ActionResponse;
import ir.bita.esm.llm.dto.ChatMessageResponse;
import ir.bita.esm.llm.entity.ChatMessage;
import ir.bita.esm.llm.entity.ChatSession;
import ir.bita.esm.llm.entity.MessageRole;
import ir.bita.esm.llm.entity.ToolExecution;
import ir.bita.esm.llm.entity.ToolExecutionStatus;
import ir.bita.esm.llm.repository.ChatMessageRepository;
import ir.bita.esm.llm.repository.ToolExecutionRepository;
import ir.bita.esm.llm.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handles action-based responses from LLM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActionHandler {

    private final ToolRegistry toolRegistry;
    private final ChatMessageRepository messageRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Handles an action from LLM by executing the corresponding tool.
     *
     * @param session The chat session
     * @param action  The action to execute
     * @return Response message
     */
    public ChatMessageResponse handle(ChatSession session, ActionResponse action) {
        String actionName = action.getAction();
        Map<String, Object> params = action.getParams();

        log.info("Handling action: {} for session: {}", actionName, session.getId());

        // Map action to tool name (they're the same in our case)
        String toolName = actionName;

        // Check if tool exists
        if (!toolRegistry.getToolNames().contains(toolName)) {
            log.error("Unknown tool: {}", toolName);
            return createErrorResponse(session, "ابزار نامعتبر: " + toolName);
        }

        // Check if tool requires confirmation
        boolean requiresConfirmation = toolRegistry.requiresConfirmation(toolName);

        // Create assistant message with the action
        String messageContent = buildMessageContent(actionName, params);
        // the assistant turn records the call it made in the same shape as a native tool_call, so
        // the history replayed to the model is consistent: the TOOL result that follows refers to
        // a call that exists (strict chat templates, e.g. gpt-oss on Groq, reject it otherwise)
        String toolCallId = "action_" + System.currentTimeMillis();
        String argumentsJson;
        try {
            argumentsJson = objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            argumentsJson = "{}";
        }
        ChatMessage assistantMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(messageContent)
                .toolCalls(List.of(Map.<String, Object>of(
                        "id", toolCallId,
                        "type", "function",
                        "function", Map.<String, Object>of("name", toolName, "arguments", argumentsJson))))
                .build();
        assistantMessage = messageRepository.save(assistantMessage);

        // Create tool execution record
        ToolExecution execution = ToolExecution.builder()
                .message(assistantMessage)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments(params)
                .requiresConfirmation(requiresConfirmation)
                .status(requiresConfirmation ? ToolExecutionStatus.PENDING : ToolExecutionStatus.CONFIRMED)
                .build();

        // Auto-execute if no confirmation needed
        if (!requiresConfirmation) {
            Map<String, Object> result = toolRegistry.executeTool(toolName, params);
            if (result.containsKey("error") && (Boolean) result.get("error")) {
                execution.markFailed((String) result.get("message"));
            } else {
                execution.markExecuted(result);
            }
        }

        execution = toolExecutionRepository.save(execution);

        // Build response - include pendingToolExecution when confirmation is required
        ChatMessageResponse response = mapToResponse(assistantMessage, List.of(execution));
        if (requiresConfirmation && execution.getStatus() == ToolExecutionStatus.PENDING) {
            response.setPendingToolExecution(mapToToolCallResponse(execution));
        }
        return response;
    }

    private String buildMessageContent(String actionName, Map<String, Object> params) {
        if ("ask_question".equals(actionName) && params != null) {
            String question = params.get("question") != null ? params.get("question").toString() : null;
            String context = params.get("context") != null ? params.get("context").toString() : null;
            if (question != null && !question.isBlank()) {
                return context != null && !context.isBlank()
                        ? String.format("%s\n\n%s", context, question)
                        : question;
            }
        }
        // For actions requiring confirmation, show exactly what will be created so the user can
        // review the values before confirming (or reject and ask for a change in the chat)
        if (params != null && !params.isEmpty() && toolRegistry.requiresConfirmation(actionName)) {
            try {
                String paramsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(params);
                return String.format("**اجرای %s**\n\nقرار است موارد زیر ساخته شود:\n\n```json\n%s\n```",
                        getActionLabel(actionName), paramsJson);
            } catch (Exception e) {
                log.warn("Failed to format params for action {}: {}", actionName, e.getMessage());
            }
        }
        return String.format("اجرای action: %s", actionName);
    }

    private String getActionLabel(String actionName) {
        return switch (actionName) {
            case "create_service" -> "ساخت سرویس";
            case "service_groovy_config" -> "پیکربندی سرویس با قالب";
            case "component_template" -> "قطعهٔ Groovy";
            case "groovy_template" -> "قالب Groovy تازه";
            case "complete" -> "اتمام";
            default -> actionName;
        };
    }

    private ChatMessageResponse createErrorResponse(ChatSession session, String errorMessage) {
        ChatMessage errorMsg = ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content("خطا: " + errorMessage)
                .build();
        errorMsg = messageRepository.save(errorMsg);

        return ChatMessageResponse.builder()
                .id(errorMsg.getId())
                .role(errorMsg.getRole())
                .content(errorMsg.getContent())
                .createdAt(errorMsg.getCreatedAt())
                .build();
    }

    private ChatMessageResponse.ToolCallResponse mapToToolCallResponse(ToolExecution e) {
        return ChatMessageResponse.ToolCallResponse.builder()
                .id(e.getToolCallId())
                .toolName(e.getToolName())
                .arguments(e.getArguments())
                .status(e.getStatus().name())
                .requiresConfirmation(e.isRequiresConfirmation())
                .result(e.getResult())
                .errorMessage(e.getErrorMessage())
                .build();
    }

    private ChatMessageResponse mapToResponse(ChatMessage message, List<ToolExecution> executions) {
        List<ChatMessageResponse.ToolCallResponse> toolCallResponses = executions.stream()
                .map(this::mapToToolCallResponse)
                .toList();

        return ChatMessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .toolCalls(toolCallResponses.isEmpty() ? null : toolCallResponses)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
