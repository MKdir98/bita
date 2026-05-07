package ir.bita.esm.llm.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for selecting LLM providers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmProviderFactory {

    private final List<LlmProvider> providers;

    /**
     * Get the default available provider.
     */
    public LlmProvider getDefaultProvider() {
        // Prefer OpenAI if available
        for (LlmProvider provider : providers) {
            if (provider.getName().equals("openai") && provider.isAvailable()) {
                return provider;
            }
        }
        // Fall back to any available provider
        for (LlmProvider provider : providers) {
            if (provider.isAvailable()) {
                return provider;
            }
        }
        throw new IllegalStateException("No LLM provider available");
    }

    /**
     * Get provider by name.
     */
    public LlmProvider getProvider(String name) {
        return providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + name));
    }

    /**
     * Get provider that supports a specific model.
     */
    public LlmProvider getProviderForModel(String model) {
        for (LlmProvider provider : providers) {
            if (provider.supportsModel(model) && provider.isAvailable()) {
                return provider;
            }
        }
        // Default to any available provider
        return getDefaultProvider();
    }

    /**
     * List all available providers.
     */
    public List<String> getAvailableProviders() {
        return providers.stream()
                .filter(LlmProvider::isAvailable)
                .map(LlmProvider::getName)
                .toList();
    }
}
