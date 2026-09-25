package ir.bita.system.support;

import ir.bita.esm.llm.dto.LlmRequest;
import ir.bita.esm.llm.dto.LlmResponse;
import ir.bita.esm.llm.provider.LlmProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records which model actually answered each real LLM call the production ChatService makes,
 * so a run can prove every answer came from the one model it reports — without changing the
 * provider. Wraps the provider bean; everything else about the call is untouched.
 */
@TestConfiguration
public class LlmCallRecorder {

    public static final List<String> ANSWERED_BY = new CopyOnWriteArrayList<>();

    @Bean
    static BeanPostProcessor recordAnsweringModel() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof LlmProvider delegate) || !"freellmapi".equals(delegate.getName())) {
                    return bean;
                }
                return new LlmProvider() {
                    @Override
                    public String getName() {
                        return delegate.getName();
                    }

                    @Override
                    public String[] getSupportedModels() {
                        return delegate.getSupportedModels();
                    }

                    @Override
                    public boolean supportsModel(String model) {
                        return delegate.supportsModel(model);
                    }

                    @Override
                    public LlmResponse chat(LlmRequest request) {
                        LlmResponse response = delegate.chat(request);
                        ANSWERED_BY.add(String.valueOf(response.getModel()));
                        return response;
                    }

                    @Override
                    public boolean isAvailable() {
                        return delegate.isAvailable();
                    }
                };
            }
        };
    }
}
