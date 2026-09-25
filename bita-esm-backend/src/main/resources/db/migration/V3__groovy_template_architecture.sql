-- Groovy Template Architecture — new tables and cleanup of deprecated route model

-- 1. groovy_template table
CREATE TABLE IF NOT EXISTS groovy_template (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    description       VARCHAR(500),
    script_text       TEXT NOT NULL,
    variables         JSONB,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_groovy_template_name ON groovy_template (name);

-- 2. component_template: ensure groovy_code column exists (may have been added in V2)
ALTER TABLE component_template ADD COLUMN IF NOT EXISTS groovy_code TEXT;

-- 3. service_groovy_config table
CREATE TABLE IF NOT EXISTS service_groovy_config (
    id                     BIGSERIAL PRIMARY KEY,
    service_id             BIGINT NOT NULL UNIQUE REFERENCES service(id),
    groovy_template_id     BIGINT NOT NULL REFERENCES groovy_template(id),
    variable_values        JSONB,
    assembled_script       TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sgc_service ON service_groovy_config (service_id);

-- Hibernate Envers audit tables for new entities
CREATE TABLE IF NOT EXISTS groovy_template_aud (
    id          BIGINT NOT NULL,
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    name        VARCHAR(100),
    description VARCHAR(500),
    script_text TEXT,
    variables   JSONB,
    deleted     BOOLEAN,
    PRIMARY KEY (id, rev)
);

CREATE TABLE IF NOT EXISTS service_groovy_config_aud (
    id                  BIGINT NOT NULL,
    rev                 INTEGER NOT NULL,
    revtype             SMALLINT,
    service_id          BIGINT,
    groovy_template_id  BIGINT,
    variable_values     JSONB,
    assembled_script    TEXT,
    PRIMARY KEY (id, rev)
);

-- 4. Drop deprecated route model tables (architecture migrated to Groovy templates)
DROP TABLE IF EXISTS component          CASCADE;
DROP TABLE IF EXISTS endpoint           CASCADE;
DROP TABLE IF EXISTS route              CASCADE;
DROP TABLE IF EXISTS endpoint_template  CASCADE;
DROP TABLE IF EXISTS route_template     CASCADE;
DROP TABLE IF EXISTS attachment         CASCADE;

-- 5. Drop deprecated enum types if they exist
DROP TYPE IF EXISTS attachment_file_type CASCADE;
