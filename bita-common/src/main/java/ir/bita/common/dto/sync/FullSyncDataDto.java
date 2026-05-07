package ir.bita.common.dto.sync;

import ir.bita.common.dto.access.ServiceAccessDto;
import ir.bita.common.dto.client.ClientDto;
import ir.bita.common.dto.route.RouteDto;
import ir.bita.common.dto.service.ServiceDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO containing all data needed for full ESB synchronization.
 * Used when ESB starts up or needs to resync completely.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullSyncDataDto {

    /**
     * Timestamp when this sync data was generated.
     */
    private LocalDateTime generatedAt;

    /**
     * The specific service ID this sync is for.
     * ESB Core pods only load data for their assigned service.
     */
    private Long serviceId;

    /**
     * The service configuration.
     */
    private ServiceDto service;

    /**
     * Routes for this service.
     */
    private List<RouteDto> routes;

    /**
     * All clients that have access to this service.
     */
    private List<ClientDto> clients;

    /**
     * Access rules for this service.
     */
    private List<ServiceAccessDto> accessRules;

    /**
     * Version number for cache invalidation.
     */
    private Long version;
}
