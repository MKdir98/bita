package ir.bita.esm.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.audit.dto.AuditHistoryResponse;
import ir.bita.esm.audit.entity.AuditRevisionEntity;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.entity.ServiceEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for querying audit history using Hibernate Envers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    private static final Map<String, Class<?>> ENTITY_TYPE_MAP = Map.of(
            "client", Client.class,
            "credential", Credential.class,
            "service", ServiceEntity.class,
            "service-collection", ServiceCollection.class,
            "service-access", ServiceAccess.class
    );

    /**
     * Get revision history for an entity.
     */
    public AuditHistoryResponse getEntityHistory(String entityType, Long entityId) {
        Class<?> entityClass = getEntityClass(entityType);
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Number> revisions = reader.getRevisions(entityClass, entityId);

        List<AuditHistoryResponse.RevisionEntry> entries = new ArrayList<>();

        for (Number revisionNumber : revisions) {
            Object entity = reader.find(entityClass, entityId, revisionNumber);
            AuditRevisionEntity revisionEntity = reader.findRevision(AuditRevisionEntity.class, revisionNumber);
            Object[] result = (Object[]) reader.createQuery()
                    .forRevisionsOfEntity(entityClass, false, true)
                    .add(AuditEntity.id().eq(entityId))
                    .add(AuditEntity.revisionNumber().eq(revisionNumber))
                    .getSingleResult();
            RevisionType revisionType = (RevisionType) result[2];

            Map<String, Object> entityData = convertEntityToMap(entity);

            entries.add(AuditHistoryResponse.RevisionEntry.builder()
                    .revisionId(revisionNumber.longValue())
                    .revisionDate(revisionEntity.getRevisionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .revisionType(revisionType.name())
                    .userId(revisionEntity.getUserId())
                    .username(revisionEntity.getUsername())
                    .entityData(entityData)
                    .build());
        }

        return AuditHistoryResponse.builder()
                .entityType(entityType)
                .entityId(entityId)
                .totalRevisions(entries.size())
                .revisions(entries)
                .build();
    }

    /**
     * Get entity state at a specific revision.
     */
    public Map<String, Object> getEntityAtRevision(String entityType, Long entityId, Long revisionId) {
        Class<?> entityClass = getEntityClass(entityType);
        AuditReader reader = AuditReaderFactory.get(entityManager);

        Object entity = reader.find(entityClass, entityId, revisionId);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found at revision " + revisionId);
        }

        AuditRevisionEntity revisionEntity = reader.findRevision(AuditRevisionEntity.class, revisionId);

        Map<String, Object> result = new HashMap<>();
        result.put("revisionId", revisionId);
        result.put("revisionDate", revisionEntity.getRevisionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("userId", revisionEntity.getUserId());
        result.put("username", revisionEntity.getUsername());
        result.put("ipAddress", revisionEntity.getIpAddress());
        result.put("entity", convertEntityToMap(entity));

        return result;
    }

    /**
     * Get all entities modified in a revision.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRevisionChanges(Long revisionId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        AuditRevisionEntity revision = reader.findRevision(AuditRevisionEntity.class, revisionId);

        if (revision == null) {
            throw new IllegalArgumentException("Revision not found: " + revisionId);
        }

        List<Map<String, Object>> changes = new ArrayList<>();

        for (Map.Entry<String, Class<?>> entry : ENTITY_TYPE_MAP.entrySet()) {
            try {
                AuditQuery query = reader.createQuery()
                        .forRevisionsOfEntity(entry.getValue(), false, true)
                        .add(AuditEntity.revisionNumber().eq(revisionId));

                List<Object[]> results = query.getResultList();
                for (Object[] row : results) {
                    Object entity = row[0];
                    RevisionType revType = (RevisionType) row[2];

                    Map<String, Object> change = new HashMap<>();
                    change.put("entityType", entry.getKey());
                    change.put("revisionType", revType.name());
                    change.put("entity", convertEntityToMap(entity));
                    changes.add(change);
                }
            } catch (Exception e) {
                log.debug("No changes for {} in revision {}", entry.getKey(), revisionId);
            }
        }

        return changes;
    }

    private Class<?> getEntityClass(String entityType) {
        Class<?> entityClass = ENTITY_TYPE_MAP.get(entityType.toLowerCase());
        if (entityClass == null) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
        return entityClass;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertEntityToMap(Object entity) {
        try {
            return objectMapper.convertValue(entity, Map.class);
        } catch (Exception e) {
            // Fallback for entities with lazy loading issues
            Map<String, Object> map = new HashMap<>();
            map.put("class", entity.getClass().getSimpleName());
            map.put("toString", entity.toString());
            return map;
        }
    }
}
