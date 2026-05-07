package ir.bita.esm.llm.provider;

import ir.bita.esm.llm.dto.LlmRequest;
import ir.bita.esm.llm.dto.LlmResponse;

/**
 * Interface for LLM providers.
 */
public interface LlmProvider {

    /**
     * Get the provider name.
     */
    String getName();

    /**
     * Get supported model names.
     */
    String[] getSupportedModels();

    /**
     * Check if this provider supports the given model.
     */
    boolean supportsModel(String model);

    /**
     * Send a chat completion request.
     */
    LlmResponse chat(LlmRequest request);

    /**
     * Check if the provider is available.
     */
    boolean isAvailable();
}
