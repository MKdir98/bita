package ir.bita.esm.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestTemplate configuration for HTTP client calls.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // The HTTP client is fixed explicitly instead of letting RestTemplateBuilder pick whatever
        // is on the classpath: with Apache HttpClient 5 present, its default retry strategy sleeps
        // for a 429/503's Retry-After — an LLM provider out of quota can send hours — and the
        // user's chat request hangs silently (the read timeout does not cover that sleep).
        // The JDK client never retries on its own; a failing provider fails fast.
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder.requestFactory(() -> factory).build();
    }
}
