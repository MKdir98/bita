package ir.bita.esm.llm.tool;

import java.util.Map;

/**
 * Interface for LLM tools (function calling).
 */
public interface LlmTool {

    /**
     * Get the tool name.
     */
    String getName();

    /**
     * Get the tool description for LLM.
     */
    String getDescription();

    /**
     * Get the JSON schema for parameters.
     */
    Map<String, Object> getParametersSchema();

    /**
     * Execute the tool with given arguments.
     */
    Map<String, Object> execute(Map<String, Object> arguments);

    /**
     * Whether this tool requires user confirmation before execution.
     */
    default boolean requiresConfirmation() {
        return false;
    }
}
