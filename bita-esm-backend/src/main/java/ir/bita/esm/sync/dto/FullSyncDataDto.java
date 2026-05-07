package ir.bita.esm.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for full sync data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullSyncDataDto {
    private LocalDateTime syncTimestamp;
    private String version;
    private List<ClientSyncDto> clients;
    private List<ServiceSyncDto> services;
    private List<AccessSyncDto> accessRules;
    private Statistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        private long totalClients;
        private long totalServices;
        private long activeServices;
        private long totalRoutes;
        private long totalAccessRules;
    }
}
