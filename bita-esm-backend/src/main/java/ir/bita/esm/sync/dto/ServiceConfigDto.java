package ir.bita.esm.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration returned to an ESB pod on startup.
 * ESB fetches this once at boot via GET /internal/v1/sync/services/{serviceId}/config.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigDto {
    private Long serviceId;
    private String name;

    /** Assembled Groovy script (component code inlined). ESB evaluates this with GroovyShell. */
    private String assembledScript;

    /** Runtime variable values injected as Groovy bindings. */
    private java.util.Map<String, Object> variableValues;

    /** PEM-encoded X.509 certs of all currently-authorised clients. */
    private List<String> authorizedClientCertPems;
    /** The template was written by the model: the ESB compiles the script in its sandbox. */
    private boolean sandboxed;
}
