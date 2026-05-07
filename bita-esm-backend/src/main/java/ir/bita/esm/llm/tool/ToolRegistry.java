package ir.bita.esm.llm.tool;

import ir.bita.esm.llm.dto.LlmRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry for all available LLM tools.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolRegistry {

    private final List<LlmTool> tools;

    /**
     * Get all tools as LLM request format.
     */
    public List<LlmRequest.Tool> getToolsForLlm() {
        return tools.stream()
                .map(tool -> LlmRequest.Tool.builder()
                        .type("function")
                        .function(LlmRequest.Function.builder()
                                .name(tool.getName())
                                .description(tool.getDescription())
                                .parameters(tool.getParametersSchema())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get a tool by name.
     */
    public LlmTool getTool(String name) {
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }

    /**
     * Execute a tool.
     */
    public Map<String, Object> executeTool(String name, Map<String, Object> arguments) {
        LlmTool tool = getTool(name);
        log.info("Executing tool: {} with args: {}", name, arguments);
        try {
            Map<String, Object> result = tool.execute(arguments);
            log.info("Tool {} executed successfully", name);
            return result;
        } catch (Exception e) {
            log.error("Tool {} failed: {}", name, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return error;
        }
    }

    /**
     * Check if a tool requires confirmation.
     */
    public boolean requiresConfirmation(String name) {
        return getTool(name).requiresConfirmation();
    }

    /**
     * List all available tool names.
     */
    public List<String> getToolNames() {
        return tools.stream()
                .map(LlmTool::getName)
                .collect(Collectors.toList());
    }
}
