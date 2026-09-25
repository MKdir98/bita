-- Minor versions of a service's configuration (1.6, 1.7 ... of service v1). Each approved
-- change is a new immutable row; the active one is mirrored into service_groovy_config, which
-- is what the ESB syncs. Rollback = make an earlier row active again.

CREATE TABLE IF NOT EXISTS service_config_version (
    id                  BIGSERIAL PRIMARY KEY,
    service_id          BIGINT NOT NULL REFERENCES service(id),
    minor               INTEGER NOT NULL,
    groovy_template_id  BIGINT NOT NULL REFERENCES groovy_template(id),
    variable_values     JSONB,
    assembled_script    TEXT,
    active              BOOLEAN NOT NULL DEFAULT FALSE,
    note                VARCHAR(500),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    version             BIGINT,
    CONSTRAINT uk_scv_service_minor UNIQUE (service_id, minor)
);

CREATE INDEX IF NOT EXISTS idx_scv_service ON service_config_version (service_id);

-- at most one active version per service
CREATE UNIQUE INDEX IF NOT EXISTS uk_scv_one_active ON service_config_version (service_id) WHERE active;

-- existing configurations become version 1 of their service, active
INSERT INTO service_config_version (service_id, minor, groovy_template_id, variable_values, assembled_script, active, note)
SELECT c.service_id, 1, c.groovy_template_id, c.variable_values, c.assembled_script, TRUE, 'migrated from service_groovy_config'
FROM service_groovy_config c
WHERE NOT EXISTS (SELECT 1 FROM service_config_version v WHERE v.service_id = c.service_id);
